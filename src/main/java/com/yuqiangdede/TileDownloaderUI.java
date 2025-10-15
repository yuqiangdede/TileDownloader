package com.yuqiangdede;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;

import java.util.Optional;
public final class TileDownloaderUI {

    private static final String PREVIEW_BASE_PATH = "/previews/";
    private static final String[] PREVIEW_TEXT_EXTENSIONS = {".html", ".htm", ".md", ".txt"};
    private static final String[] PREVIEW_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif"};
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s<>\"]+)");

    private TileDownloaderUI() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ignored) {
        }
        SwingUtilities.invokeLater(TileDownloaderUI::createAndShow);
    }

    private static void createAndShow() {
        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        toolTipManager.setInitialDelay(200);
        toolTipManager.setDismissDelay(Math.max(toolTipManager.getDismissDelay(), 15_000));

        TileDownloadConfig defaults = TileDownloadConfig.defaults();

        JFrame frame = new JFrame("瓦片下载器");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextField directoryField = new JTextField(defaults.getBaseDirectory().toString(), 25);
        JButton browseButton = new JButton("浏览...");
        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        dirPanel.add(directoryField, BorderLayout.CENTER);
        dirPanel.add(browseButton, BorderLayout.EAST);
        addRow(settingsPanel, gbc, "输出目录", dirPanel);
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(directoryField.getText().trim());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                directoryField.setText(chooser.getSelectedFile().toPath().toString());
            }
        });
        JSpinner minZoomSpinner = new JSpinner(new SpinnerNumberModel(defaults.getMinZoom(), 0, 22, 1));
        JSpinner maxZoomSpinner = new JSpinner(new SpinnerNumberModel(defaults.getMaxZoom(), 0, 22, 1));
        minZoomSpinner.setValue(0);
        maxZoomSpinner.setValue(13);
        addRow(settingsPanel, gbc, "缩放级别", rangePanel(minZoomSpinner, maxZoomSpinner));
        JSpinner threadSpinner = new JSpinner(new SpinnerNumberModel(defaults.getThreads(), 1, 1024, 1));
        addRow(settingsPanel, gbc, "线程数", threadSpinner);
        JCheckBox proxyCheckBox = new JCheckBox("启用 SOCKS 代理");
        addRow(settingsPanel, gbc, "", proxyCheckBox);
        String defaultProxyHost = defaults.getProxyHost();
        if (defaultProxyHost == null || defaultProxyHost.isBlank()) {
            defaultProxyHost = "127.0.0.1";
        }
        JTextField proxyHostField = new JTextField(defaultProxyHost, 12);
        int defaultProxyPort = defaults.getProxyPort() > 0 ? defaults.getProxyPort() : 7739;
        JSpinner proxyPortSpinner = new JSpinner(new SpinnerNumberModel(defaultProxyPort, 0, 65535, 1));
        proxyCheckBox.setSelected(defaults.isUseProxy());
        proxyHostField.setEnabled(defaults.isUseProxy());
        proxyPortSpinner.setEnabled(defaults.isUseProxy());
        JPanel proxyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        proxyPanel.add(new JLabel("主机"));
        proxyPanel.add(proxyHostField);
        proxyPanel.add(new JLabel("端口"));
        proxyPanel.add(proxyPortSpinner);
        addRow(settingsPanel, gbc, "", proxyPanel);
        String defaultBounds = String.format(Locale.ROOT, "%.5f %.5f, %.5f %.5f",
            defaults.getStartLat(),
            defaults.getStartLon(),
            defaults.getEndLat(),
            defaults.getEndLon());
        JTextArea boundsTextArea = new JTextArea(defaultBounds, 4, 26);
        boundsTextArea.setLineWrap(true);
        boundsTextArea.setWrapStyleWord(true);
        boundsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        boundsTextArea.setMargin(new Insets(4, 4, 4, 4));
        boundsTextArea.setToolTipText("每行格式：纬度 经度,纬度 经度（支持多行输入）");
        JScrollPane boundsScrollPane = new JScrollPane(boundsTextArea);
        boundsScrollPane.setPreferredSize(new Dimension(10, 90));
        addRow(settingsPanel, gbc, "经纬度范围", boundsScrollPane);
        Map<String, String> sourceDisplayNames = TileDownloader.getSourceDisplayNames();
        List<SourceEntry> sourceEntries = new ArrayList<>();
        Map<String, Optional<SourcePreview>> previewCache = new LinkedHashMap<>();
        LinkedHashMap<String, List<SourceEntry>> groupedSources = new LinkedHashMap<>();
        // Normalise display names so related sources (e.g. multiple Gaode flavours) share the same row.
        Map<String, String> groupOverrides = Map.of(
            "基础海图", "海图",
            "海图航标", "海图",
            "高德-卫星", "高德",
            "高德-混合", "高德",
            "高德-路网", "高德",
            "高德-浅色1", "高德",
            "高德-浅色2", "高德"
        );
        for (Map.Entry<String, String> entry : sourceDisplayNames.entrySet()) {
            String displayName = entry.getValue();
            String groupName = displayName;
            String itemLabel = displayName;
            // Split naming patterns such as "ArcGIS 地形" so the prefix controls grouping and the suffix becomes the checkbox label.
            int firstSpace = displayName.indexOf(' ');
            if (firstSpace > 0) {
                groupName = displayName.substring(0, firstSpace).trim();
                itemLabel = displayName.substring(firstSpace + 1).trim();
                if (itemLabel.isEmpty()) {
                    itemLabel = displayName;
                }
            }
            // Fallback when there is no space: treat ASCII or full-width hyphen as the separator (e.g. "高德-卫星").
            int hyphenIndex = displayName.indexOf('-');
            if (hyphenIndex < 0) {
                hyphenIndex = displayName.indexOf('－');
            }
            if (hyphenIndex > 0 && (firstSpace <= 0 || groupName.equals(displayName))) {
                groupName = displayName.substring(0, hyphenIndex).trim();
                itemLabel = displayName.substring(hyphenIndex + 1).trim();
                if (itemLabel.isEmpty()) {
                    itemLabel = displayName;
                }
            }
            groupName = groupOverrides.getOrDefault(groupName, groupName);
            JCheckBox box = new JCheckBox(itemLabel, false);
            Optional<SourcePreview> cachedPreview = previewCache.get(entry.getKey());
            if (cachedPreview == null) {
                SourcePreview loaded = loadPreview(entry.getKey());
                cachedPreview = Optional.ofNullable(loaded);
                previewCache.put(entry.getKey(), cachedPreview);
            }
            SourcePreview preview = cachedPreview.orElse(null);
            SourceEntry entryRecord = new SourceEntry(entry.getKey(), displayName, box, preview);
            sourceEntries.add(entryRecord);
            groupedSources.computeIfAbsent(groupName, key -> new ArrayList<>()).add(entryRecord);
        }
        JPanel sourcePanel = new JPanel();
        sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.Y_AXIS));
        // Some providers expose many variants (e.g. 谷歌), render them on two lines for readability.
        Set<String> multiLineGroups = Set.of("谷歌");
        for (Map.Entry<String, List<SourceEntry>> groupEntry : groupedSources.entrySet()) {
            String groupName = groupEntry.getKey();
            List<SourceEntry> entries = groupEntry.getValue();
            JPanel container = new JPanel();
            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
            container.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel firstLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            JLabel groupLabel = new JLabel(groupName + "：");
            groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD));
            firstLine.add(groupLabel);
            if (multiLineGroups.contains(groupName) && entries.size() > 3) {
                int split = (entries.size() + 1) / 2;
                for (int i = 0; i < split; i++) {
                    firstLine.add(entries.get(i).checkBox());
                }
                container.add(firstLine);
                JPanel secondLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
                int indent = groupLabel.getPreferredSize().width;
                secondLine.add(Box.createHorizontalStrut(indent));
                for (int i = split; i < entries.size(); i++) {
                    secondLine.add(entries.get(i).checkBox());
                }
                container.add(secondLine);
            } else {
                for (SourceEntry entryRecord : entries) {
                    firstLine.add(entryRecord.checkBox());
                }
                container.add(firstLine);
            }
            sourcePanel.add(container);
        }
        JScrollPane sourceScroll = new JScrollPane(sourcePanel);
        sourceScroll.setBorder(new TitledBorder("地图源（可多选）"));
        sourceScroll.setPreferredSize(new Dimension(0, 240));
        sourceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel sourceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton selectAllButton = new JButton("全选");
        JButton deselectAllButton = new JButton("全不选");
        sourceButtons.add(selectAllButton);
        sourceButtons.add(deselectAllButton);
        JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        leftPanel.setPreferredSize(new Dimension(360, 0));
        leftPanel.add(settingsPanel, BorderLayout.NORTH);
        leftPanel.add(sourceScroll, BorderLayout.CENTER);
        leftPanel.add(sourceButtons, BorderLayout.SOUTH);
        proxyCheckBox.addActionListener(e -> {
            boolean enabled = proxyCheckBox.isSelected();
            proxyHostField.setEnabled(enabled);
            proxyPortSpinner.setEnabled(enabled);
        });
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createEmptyBorder());
        logScroll.setPreferredSize(new Dimension(640, 360));
        PreviewPanel previewPanel = new PreviewPanel();
        CardLayout logCardLayout = new CardLayout();
        JPanel logCardPanel = new JPanel(logCardLayout);
        logCardPanel.setBorder(new TitledBorder("下载日志"));
        logCardPanel.add(logScroll, "log");
        logCardPanel.add(previewPanel, "preview");
        logCardLayout.show(logCardPanel, "log");
        PreviewDisplay previewDisplay = new PreviewDisplay(logCardPanel, logCardLayout, previewPanel);
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 10));
        centerPanel.add(leftPanel, BorderLayout.WEST);
        centerPanel.add(logCardPanel, BorderLayout.CENTER);
        frame.add(centerPanel, BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("准备就绪");
        bottomPanel.add(progressBar, BorderLayout.CENTER);
        JButton clearLogButton = new JButton("清空日志");
        JButton startButton = new JButton("开始下载");
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsPanel.add(clearLogButton);
        buttonsPanel.add(startButton);
        bottomPanel.add(buttonsPanel, BorderLayout.EAST);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        clearLogButton.addActionListener(e -> logArea.setText(""));
        Runnable updateSourceSelection = () -> {
            LinkedHashSet<String> selectedIds = collectSelectedIds(sourceEntries);
            boolean hasSelection = !selectedIds.isEmpty();
            startButton.setEnabled(hasSelection);
        };
        for (SourceEntry entry : sourceEntries) {
            JCheckBox box = entry.checkBox();
            box.addActionListener(e -> updateSourceSelection.run());
            box.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    previewDisplay.showPreview(entry.name(), entry.preview());
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    previewDisplay.hidePreview();
                }
            });
        }
        selectAllButton.addActionListener(e -> {
            sourceEntries.forEach(entry -> entry.checkBox().setSelected(true));
            updateSourceSelection.run();
        });
        deselectAllButton.addActionListener(e -> {
            sourceEntries.forEach(entry -> entry.checkBox().setSelected(false));
            updateSourceSelection.run();
        });
        updateSourceSelection.run();
        startButton.addActionListener(e -> {
            try {
                LinkedHashSet<String> selectedIds = collectSelectedIds(sourceEntries);
                List<TileDownloadConfig> configs = buildConfigs(
                    directoryField.getText(),
                    boundsTextArea.getText(),
                    (int) minZoomSpinner.getValue(),
                    (int) maxZoomSpinner.getValue(),
                    (int) threadSpinner.getValue(),
                    selectedIds,
                    proxyCheckBox.isSelected(),
                    proxyHostField.getText(),
                    (int) proxyPortSpinner.getValue());
                launchDownload(configs, startButton, progressBar, logArea);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(frame, ex.getMessage(), "输入错误", JOptionPane.ERROR_MESSAGE);
            }
        });
        frame.setSize(960, 640);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel rangePanel(Component start, Component end) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.add(start);
        panel.add(new JLabel("至"));
        panel.add(end);
        return panel;
    }

    private static LinkedHashSet<String> collectSelectedIds(List<SourceEntry> sources) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (SourceEntry entry : sources) {
            if (entry.checkBox().isSelected()) {
                ids.add(entry.id());
            }
        }
        return ids;
    }

    // Validate the form input and translate it into a TileDownloadConfig used by the downloader.
    private static List<TileDownloadConfig> buildConfigs(String directoryText,
                                                         String boundsText,
                                                         int minZoom,
                                                         int maxZoom,
                                                         int threads,
                                                         Set<String> selectedIds,
                                                         boolean useProxy,
                                                         String proxyHostText,
                                                         int proxyPortValue) {
        if (minZoom > maxZoom) {
            throw new IllegalArgumentException("最小缩放级别不能大于最大缩放级别。");
        }
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个地图源。");
        }
        Path directory;
        try {
            directory = Paths.get(directoryText.trim());
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("输出目录无效。");
        }
        List<CoordinateBounds> boundsList = parseBounds(boundsText);
        String trimmedProxyHost = proxyHostText == null ? "" : proxyHostText.trim();
        int proxyPort = Math.max(0, proxyPortValue);
        if (useProxy) {
            if (trimmedProxyHost.isEmpty()) {
                throw new IllegalArgumentException("必须填写代理主机。");
            }
            if (proxyPort <= 0) {
                throw new IllegalArgumentException("代理端口必须大于零。");
            }
        }
        List<TileDownloadConfig> configs = new ArrayList<>(boundsList.size());
        for (CoordinateBounds bounds : boundsList) {
            TileDownloadConfig config = TileDownloadConfig.builder()
                .baseDirectory(directory)
                .longitudeRange(bounds.minLon(), bounds.maxLon())
                .latitudeRange(bounds.minLat(), bounds.maxLat())
                .zoomRange(minZoom, maxZoom)
                .threads(threads)
                .downloadType("all")
                .proxy(useProxy, trimmedProxyHost, proxyPort)
                .build();
            config.setSelectedSourceIds(selectedIds);
            config.setUseExplicitSources(true);
            configs.add(config);
        }
        return configs;
    }

    private static List<CoordinateBounds> parseBounds(String text) {
        String trimmedInput = text == null ? "" : text.trim();
        if (trimmedInput.isEmpty()) {
            throw new IllegalArgumentException("经纬度范围不能为空。");
        }
        String[] rows = trimmedInput.split("\\R");
        List<CoordinateBounds> bounds = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i].trim();
            if (row.isEmpty()) {
                continue;
            }
            row = row.replace('，', ',');
            String[] parts = row.split("\\s*,\\s*");
            if (parts.length != 2) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行格式无效：应为“纬度 经度,纬度 经度”。");
            }
            double[] first = parseLatLon(parts[0], i + 1, 1);
            double[] second = parseLatLon(parts[1], i + 1, 2);
            double minLat = Math.min(first[0], second[0]);
            double maxLat = Math.max(first[0], second[0]);
            double minLon = Math.min(first[1], second[1]);
            double maxLon = Math.max(first[1], second[1]);
            if (Double.compare(minLon, maxLon) >= 0) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行经度范围无效：最小经度必须小于最大经度。");
            }
            if (Double.compare(minLat, maxLat) >= 0) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行纬度范围无效：最小纬度必须小于最大纬度。");
            }
            bounds.add(new CoordinateBounds(minLat, maxLat, minLon, maxLon));
        }
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("经纬度范围不能为空。");
        }
        return bounds;
    }

    private static double[] parseLatLon(String token, int lineNumber, int pairIndex) {
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("第 " + lineNumber + " 行缺少第 " + pairIndex + " 个坐标。");
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("第 " + lineNumber + " 行第 " + pairIndex + " 个坐标格式无效：应为“纬度 经度”。");
        }
        double latitude = parseCoordinate("第 " + lineNumber + " 行第 " + pairIndex + " 个坐标的纬度", parts[0], -90.0, 90.0);
        double longitude = parseCoordinate("第 " + lineNumber + " 行第 " + pairIndex + " 个坐标的经度", parts[1], -180.0, 180.0);
        return new double[]{latitude, longitude};
    }

    // Reusable parser that validates numeric text fields and enforces allowed ranges.
    private static double parseCoordinate(String label, String text, double minAllowed, double maxAllowed) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空。");
        }
        double value;
        try {
            value = Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "格式无效。");
        }
        if (value < minAllowed || value > maxAllowed) {
            throw new IllegalArgumentException(label + "必须介于 " + minAllowed + " 与 " + maxAllowed + " 之间。");
        }
        return value;
    }

    private static void launchDownload(List<TileDownloadConfig> configs,
                                       JButton startButton,
                                       JProgressBar progressBar,
                                       JTextArea logArea) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        startButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("正在下载...");
        appendLog(logArea, "开始下载...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                try (TextAreaOutputStream taos = new TextAreaOutputStream(logArea);
                     PrintStream redirect = new PrintStream(taos, true, StandardCharsets.UTF_8)) {
                    System.setOut(redirect);
                    System.setErr(redirect);
                    int total = configs.size();
                    boolean completedAll = true;
                    for (int i = 0; i < total; i++) {
                        TileDownloadConfig config = configs.get(i);
                        appendLog(logArea, String.format(
                            Locale.ROOT,
                            "处理第 %d/%d 个范围：%.5f %.5f, %.5f %.5f",
                            i + 1,
                            total,
                            config.getStartLat(),
                            config.getStartLon(),
                            config.getEndLat(),
                            config.getEndLon()));
                        try {
                            Files.createDirectories(config.getBaseDirectory());
                        } catch (IOException ex) {
                            appendLog(logArea, "创建目录失败：" + ex.getMessage());
                            completedAll = false;
                            break;
                        }
                        try {
                            TileDownloader.run(config);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            appendLog(logArea, "下载已中断。");
                            completedAll = false;
                            break;
                        } catch (RuntimeException ex) {
                            appendLog(logArea, "下载失败：" + ex.getMessage());
                            completedAll = false;
                            break;
                        }
                    }
                    if (completedAll) {
                        appendLog(logArea, "全部范围处理完成。");
                    }
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
                return null;
            }
            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setString("已完成");
                startButton.setEnabled(true);
            }
        };
        worker.execute();
    }

    private static void appendLog(JTextArea logArea, String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message);
            logArea.append(System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private static SourcePreview loadPreview(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return null;
        }
        URL imageUrl = null;
        for (String extension : PREVIEW_IMAGE_EXTENSIONS) {
            imageUrl = TileDownloaderUI.class.getResource(PREVIEW_BASE_PATH + sourceId + extension);
            if (imageUrl == null) {
                imageUrl = TileDownloaderUI.class.getResource("/" + sourceId + extension);
            }
            if (imageUrl != null) {
                break;
            }
        }
        BufferedImage image = null;
        if (imageUrl != null) {
            try (InputStream stream = imageUrl.openStream()) {
                image = ImageIO.read(stream);
            } catch (IOException ex) {
                System.err.println("读取地图源示意图失败：" + imageUrl + "，原因：" + ex.getMessage());
            }
        }
        String description = null;
        boolean descriptionIsHtml = false;
        for (String extension : PREVIEW_TEXT_EXTENSIONS) {
            String resourcePath = PREVIEW_BASE_PATH + sourceId + extension;
            try (InputStream stream = TileDownloaderUI.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    continue;
                }
                description = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
                descriptionIsHtml = ".html".equalsIgnoreCase(extension) || ".htm".equalsIgnoreCase(extension);
                break;
            } catch (IOException ex) {
                System.err.println("读取地图源预览说明失败：" + resourcePath + "，原因：" + ex.getMessage());
            }
        }
        if ((description == null || description.isBlank()) && image == null) {
            return null;
        }
        return new SourcePreview(description, descriptionIsHtml, imageUrl, image);
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String plainTextToHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String escaped = escapeHtml(value);
        Matcher matcher = URL_PATTERN.matcher(escaped);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(1);
            matcher.appendReplacement(buffer, "<a href=\"" + url + "\">" + url + "</a>");
        }
        matcher.appendTail(buffer);
        return buffer.toString()
            .replace("\r\n", "<br>")
            .replace("\r", "<br>")
            .replace("\n", "<br>");
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, String labelText, Component component) {
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(labelText), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(component, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
    }

    // Redirects System.out/System.err into the Swing text area while keeping writes thread-safe.
    private static final class TextAreaOutputStream extends OutputStream implements AutoCloseable {
        private final JTextArea textArea;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private volatile boolean closed;
        private TextAreaOutputStream(JTextArea textArea) {
            this.textArea = textArea;
        }
        @Override
        public synchronized void write(int b) {
            if (closed) {
                return;
            }
            if (b == '\r') {
                return;
            }
            if (b == '\n') {
                flushBuffer(true);
            } else {
                buffer.write(b);
            }
        }
        @Override
        public synchronized void flush() {
            flushBuffer(false);
        }
        @Override
        public synchronized void close() {
            closed = true;
            flushBuffer(false);
        }
        private void flushBuffer(boolean appendNewLine) {
            if (buffer.size() == 0 && !appendNewLine) {
                return;
            }
            String text = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            if (appendNewLine) {
                text += System.lineSeparator();
            }
            appendToArea(text);
        }
        private void appendToArea(String text) {
            if (text.isEmpty()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                textArea.append(text);
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }
    }

    private static final class PreviewDisplay {
        private final JPanel container;
        private final CardLayout layout;
        private final PreviewPanel previewPanel;
        private final Timer hideTimer;

        private PreviewDisplay(JPanel container, CardLayout layout, PreviewPanel previewPanel) {
            this.container = container;
            this.layout = layout;
            this.previewPanel = previewPanel;
            this.hideTimer = new Timer(200, e -> this.layout.show(this.container, "log"));
            this.hideTimer.setRepeats(false);
        }

        private void showPreview(String title, SourcePreview preview) {
            hideTimer.stop();
            previewPanel.updateContent(title, preview);
            layout.show(container, "preview");
            previewPanel.refreshImage();
        }

        private void hidePreview() {
            hideTimer.restart();
        }
    }

    private static final class PreviewPanel extends JPanel {
        private static final Color ACCENT_COLOR = new Color(0xCC0000);
        private static final Dimension IMAGE_AREA = new Dimension(520, 320);
        private final JEditorPane descriptionPane;
        private final JLabel imageLabel;
        private SourcePreview currentPreview;

        private PreviewPanel() {
            setOpaque(true);
            setBackground(Color.WHITE);
            setLayout(new BorderLayout(12, 12));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT_COLOR, 1, true),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
                )
            ));
            descriptionPane = new JEditorPane();
            descriptionPane.setEditable(false);
            descriptionPane.setOpaque(false);
            descriptionPane.setContentType("text/html");
            descriptionPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            descriptionPane.setFont(descriptionPane.getFont().deriveFont(Font.PLAIN, 13f));
            imageLabel = new JLabel();
            imageLabel.setOpaque(true);
            imageLabel.setBackground(Color.WHITE);
            imageLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_COLOR, 1, true),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));
            imageLabel.setPreferredSize(IMAGE_AREA);
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);
            imageLabel.setForeground(ACCENT_COLOR);
            add(descriptionPane, BorderLayout.NORTH);
            add(imageLabel, BorderLayout.CENTER);
        }

        private void updateContent(String title, SourcePreview preview) {
            this.currentPreview = preview;
            if (preview != null) {
                descriptionPane.setText(preview.buildDescriptionHtml(title));
            } else {
                descriptionPane.setText(buildFallbackDescription(title));
            }
            descriptionPane.setCaretPosition(0);
            applyImage();
        }

        private void applyImage() {
            SourcePreview preview = currentPreview;
            if (preview != null && preview.hasImage()) {
                Dimension size = imageLabel.getSize();
                if (size.width <= 0 || size.height <= 0) {
                    size = imageLabel.getPreferredSize();
                }
                int availableWidth = Math.max(1, size.width - 24);
                int availableHeight = Math.max(1, size.height - 24);
                ImageIcon icon = preview.scaledIcon(availableWidth, availableHeight);
                if (icon != null) {
                    imageLabel.setIcon(icon);
                    imageLabel.setText(null);
                    return;
                }
            }
            imageLabel.setIcon(null);
            imageLabel.setText("暂无示意图片");
        }

        private void refreshImage() {
            SwingUtilities.invokeLater(this::applyImage);
        }

        private static String buildFallbackDescription(String title) {
            StringBuilder html = new StringBuilder("<html><body style='margin:0;font-family:\"Microsoft YaHei\",sans-serif;font-size:13px;color:#cc0000;'>");
            if (title != null && !title.isBlank()) {
                html.append("<div style='font-weight:bold;margin-bottom:6px;'>")
                    .append(escapeHtml(title))
                    .append("</div>");
            }
            html.append("<div>暂未提供该地图源的示意信息。</div>");
            html.append("</body></html>");
            return html.toString();
        }
    }

    private record CoordinateBounds(double minLat, double maxLat, double minLon, double maxLon) {
    }

    private record SourceEntry(String id, String name, JCheckBox checkBox, SourcePreview preview) {
    }

    private record SourcePreview(String description, boolean descriptionIsHtml, URL imageUrl, BufferedImage image) {
        private String buildDescriptionHtml(String title) {
            StringBuilder html = new StringBuilder("<html><body style='margin:0;font-family:\"Microsoft YaHei\",sans-serif;font-size:13px;color:#cc0000;'>");
            if (title != null && !title.isBlank()) {
                html.append("<div style='font-weight:bold;margin-bottom:6px;'>")
                    .append(escapeHtml(title))
                    .append("</div>");
            }
            if (description != null && !description.isBlank()) {
                if (descriptionIsHtml) {
                    html.append("<div>").append(description).append("</div>");
                } else {
                    html.append("<div>").append(plainTextToHtml(description)).append("</div>");
                }
            } else {
                html.append("<div>暂无文字说明。</div>");
            }
            html.append("</body></html>");
            return html.toString();
        }

        private boolean hasImage() {
            return image != null;
        }

        private ImageIcon scaledIcon(int maxWidth, int maxHeight) {
            if (image == null || maxWidth <= 0 || maxHeight <= 0) {
                return null;
            }
            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            double scale = Math.min((double) maxWidth / originalWidth, (double) maxHeight / originalHeight);
            if (scale > 1.0) {
                scale = 1.0;
            }
            int width = Math.max(1, (int) Math.round(originalWidth * scale));
            int height = Math.max(1, (int) Math.round(originalHeight * scale));
            if (width == originalWidth && height == originalHeight) {
                return new ImageIcon(image);
            }
            Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }
    }
}

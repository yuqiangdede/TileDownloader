package com.yuqiangdede;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;

public final class TileDownloaderUI {

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
        JSpinner threadSpinner = new JSpinner(new SpinnerNumberModel(defaults.getThreads(), 1, 64, 1));
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
        JTextField minLonField = new JTextField(String.format("%.5f", defaults.getStartLon()), 10);
        JTextField maxLonField = new JTextField(String.format("%.5f", defaults.getEndLon()), 10);
        addRow(settingsPanel, gbc, "经度范围", rangePanel(minLonField, maxLonField));
        JTextField minLatField = new JTextField(String.format("%.5f", defaults.getStartLat()), 10);
        JTextField maxLatField = new JTextField(String.format("%.5f", defaults.getEndLat()), 10);
        addRow(settingsPanel, gbc, "纬度范围", rangePanel(minLatField, maxLatField));
        Map<String, String> sourceDisplayNames = TileDownloader.getSourceDisplayNames();
        List<SourceEntry> sourceEntries = new ArrayList<>();
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
            String override = groupOverrides.getOrDefault(groupName, groupName);
            groupName = override;
            JCheckBox box = new JCheckBox(itemLabel, false);
            SourceEntry entryRecord = new SourceEntry(entry.getKey(), displayName, box);
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
        logScroll.setBorder(new TitledBorder("下载日志"));
        logScroll.setPreferredSize(new Dimension(640, 360));
        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 10));
        centerPanel.add(leftPanel, BorderLayout.WEST);
        centerPanel.add(logScroll, BorderLayout.CENTER);
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
            entry.checkBox().addActionListener(e -> updateSourceSelection.run());
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
                TileDownloadConfig config = buildConfig(
                    directoryField.getText(),
                    minLonField.getText(),
                    maxLonField.getText(),
                    minLatField.getText(),
                    maxLatField.getText(),
                    (int) minZoomSpinner.getValue(),
                    (int) maxZoomSpinner.getValue(),
                    (int) threadSpinner.getValue(),
                    selectedIds,
                    proxyCheckBox.isSelected(),
                    proxyHostField.getText(),
                    (int) proxyPortSpinner.getValue());
                launchDownload(config, startButton, progressBar, logArea);
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
    private static TileDownloadConfig buildConfig(String directoryText,
                                                  String minLonText,
                                                  String maxLonText,
                                                  String minLatText,
                                                  String maxLatText,
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
        double minLon = parseCoordinate("最小经度", minLonText, -180.0, 180.0);
        double maxLon = parseCoordinate("最大经度", maxLonText, -180.0, 180.0);
        double minLat = parseCoordinate("最小纬度", minLatText, -90.0, 90.0);
        double maxLat = parseCoordinate("最大纬度", maxLatText, -90.0, 90.0);
        if (minLon >= maxLon) {
            throw new IllegalArgumentException("最小经度必须小于最大经度。");
        }
        if (minLat >= maxLat) {
            throw new IllegalArgumentException("最小纬度必须小于最大纬度。");
        }
        Path directory;
        try {
            directory = Paths.get(directoryText.trim());
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("输出目录无效。");
        }
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
        TileDownloadConfig config = TileDownloadConfig.builder()
            .baseDirectory(directory)
            .longitudeRange(minLon, maxLon)
            .latitudeRange(minLat, maxLat)
            .zoomRange(minZoom, maxZoom)
            .threads(threads)
            .downloadType("all")
            .proxy(useProxy, trimmedProxyHost, proxyPort)
            .build();
        config.setSelectedSourceIds(selectedIds);
        config.setUseExplicitSources(true);
        return config;
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
    }    private static void launchDownload(TileDownloadConfig config,
                                       JButton startButton,
                                       JProgressBar progressBar,
                                       JTextArea logArea) {
        startButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("正在下载...");
        appendLog(logArea, "开始下载...");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                try {
                    Files.createDirectories(config.getBaseDirectory());
                } catch (IOException ex) {
                    appendLog(logArea, "创建目录失败：" + ex.getMessage());
                    return null;
                }
                try (TextAreaOutputStream taos = new TextAreaOutputStream(logArea);
                     PrintStream redirect = new PrintStream(taos, true, StandardCharsets.UTF_8)) {
                    System.setOut(redirect);
                    System.setErr(redirect);
                    TileDownloader.run(config);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    appendLog(logArea, "下载已中断。");
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
            String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
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

    private record SourceEntry(String id, String name, JCheckBox checkBox) {
    }
}

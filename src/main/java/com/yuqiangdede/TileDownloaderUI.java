package com.yuqiangdede;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple Swing front-end for configuring and launching tile downloads.
 */
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

        JFrame frame = new JFrame("Tile Downloader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Output directory
        JTextField directoryField = new JTextField(defaults.getBaseDirectory().toString(), 25);
        JButton browseButton = new JButton("浏览...");
        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        dirPanel.add(directoryField, BorderLayout.CENTER);
        dirPanel.add(browseButton, BorderLayout.EAST);
        addRow(formPanel, gbc, "输出目录", dirPanel);

        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(directoryField.getText().trim());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                directoryField.setText(chooser.getSelectedFile().toPath().toString());
            }
        });

        // Longitude range
        JTextField startLonField = new JTextField(String.valueOf(defaults.getStartLon()), 8);
        JTextField endLonField = new JTextField(String.valueOf(defaults.getEndLon()), 8);
        JPanel lonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        lonPanel.add(startLonField);
        lonPanel.add(new JLabel("→"));
        lonPanel.add(endLonField);
        addRow(formPanel, gbc, "经度范围", lonPanel);

        // Latitude range
        JTextField startLatField = new JTextField(String.valueOf(defaults.getStartLat()), 8);
        JTextField endLatField = new JTextField(String.valueOf(defaults.getEndLat()), 8);
        JPanel latPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        latPanel.add(startLatField);
        latPanel.add(new JLabel("→"));
        latPanel.add(endLatField);
        addRow(formPanel, gbc, "纬度范围", latPanel);

        // Zoom range
        JSpinner minZoomSpinner = new JSpinner(new SpinnerNumberModel(defaults.getMinZoom(), 0, 22, 1));
        JSpinner maxZoomSpinner = new JSpinner(new SpinnerNumberModel(defaults.getMaxZoom(), 0, 22, 1));
        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        zoomPanel.add(minZoomSpinner);
        zoomPanel.add(new JLabel("→"));
        zoomPanel.add(maxZoomSpinner);
        addRow(formPanel, gbc, "缩放层级", zoomPanel);

        // Threads
        JSpinner threadSpinner = new JSpinner(new SpinnerNumberModel(defaults.getThreads(), 1, 64, 1));
        addRow(formPanel, gbc, "并发线程数", threadSpinner);

        // Download type combo
        List<String> typeOptions = new ArrayList<>(TileDownloader.getDownloadTypeKeys());
        JComboBox<String> downloadTypeCombo = new JComboBox<>(typeOptions.toArray(new String[0]));
        downloadTypeCombo.setSelectedItem(defaults.getDownloadType());
        addRow(formPanel, gbc, "下载类型", downloadTypeCombo);

        // Manual source selection
        JCheckBox manualCheckBox = new JCheckBox("手动选择地图源");
        addRow(formPanel, gbc, "", manualCheckBox);

        // Proxy configuration
        JCheckBox proxyCheckBox = new JCheckBox("启用 SOCKS 代理");
        addRow(formPanel, gbc, "", proxyCheckBox);
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
        addRow(formPanel, gbc, "", proxyPanel);

        Map<String, String> sourceNames = TileDownloader.getSourceDisplayNames();
        DefaultListModel<TileSourceOption> sourceModel = new DefaultListModel<>();
        for (Map.Entry<String, String> entry : sourceNames.entrySet()) {
            sourceModel.addElement(new TileSourceOption(entry.getKey(), entry.getValue()));
        }
        JList<TileSourceOption> sourceList = new JList<>(sourceModel);
        sourceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        sourceList.setEnabled(false);
        sourceList.setVisibleRowCount(6);
        sourceList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.display());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });
        JScrollPane sourceScroll = new JScrollPane(sourceList);
        sourceScroll.setBorder(new TitledBorder("地图源（可多选）"));

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(sourceScroll, gbc);

        manualCheckBox.addActionListener(e -> {
            boolean enabled = manualCheckBox.isSelected();
            sourceList.setEnabled(enabled);
            downloadTypeCombo.setEnabled(!enabled);
            if (!enabled) {
                sourceList.clearSelection();
            }
        });

        proxyCheckBox.addActionListener(e -> {
            boolean enabled = proxyCheckBox.isSelected();
            proxyHostField.setEnabled(enabled);
            proxyPortSpinner.setEnabled(enabled);
        });

        frame.add(formPanel, BorderLayout.NORTH);

        // Log area
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("运行日志"));
        frame.add(logScroll, BorderLayout.CENTER);

        // Bottom panel with progress and actions
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("就绪");
        bottomPanel.add(progressBar, BorderLayout.CENTER);

        JButton startButton = new JButton("开始下载");
        JButton clearButton = new JButton("清空日志");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.add(clearButton);
        buttonPanel.add(startButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        clearButton.addActionListener(e -> logArea.setText(""));

        startButton.addActionListener(e -> {
            try {
                TileDownloadConfig config = buildConfig(directoryField.getText(),
                    startLonField.getText(),
                    endLonField.getText(),
                    startLatField.getText(),
                    endLatField.getText(),
                    (int) minZoomSpinner.getValue(),
                    (int) maxZoomSpinner.getValue(),
                    (int) threadSpinner.getValue(),
                    manualCheckBox.isSelected(),
                    sourceList.getSelectedValuesList(),
                    (String) downloadTypeCombo.getSelectedItem(),
                    proxyCheckBox.isSelected(),
                    proxyHostField.getText(),
                    (int) proxyPortSpinner.getValue());

                launchDownload(config, startButton, progressBar, logArea);
            } catch (IllegalArgumentException ex1) {
                JOptionPane.showMessageDialog(frame, ex1.getMessage(), "输入错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.setSize(820, 640);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static TileDownloadConfig buildConfig(String directoryText,
                                                  String startLonText,
                                                  String endLonText,
                                                  String startLatText,
                                                  String endLatText,
                                                  int minZoom,
                                                  int maxZoom,
                                                  int threads,
                                                  boolean manualSelection,
                                                  List<TileSourceOption> selectedSources,
                                                  String downloadType,
                                                  boolean useProxy,
                                                  String proxyHostText,
                                                  int proxyPortValue) {
        if (minZoom > maxZoom) {
            throw new IllegalArgumentException("最小缩放等级不能大于最大缩放等级。");
        }

        Path directory;
        try {
            directory = Paths.get(directoryText.trim());
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("输出目录路径无效。");
        }

        double startLon = parseDouble(startLonText, "起始经度");
        double endLon = parseDouble(endLonText, "结束经度");
        double startLat = parseDouble(startLatText, "起始纬度");
        double endLat = parseDouble(endLatText, "结束纬度");

        if (startLon > endLon) {
            throw new IllegalArgumentException("起始经度不能大于结束经度。");
        }
        if (startLat > endLat) {
            throw new IllegalArgumentException("起始纬度不能大于结束纬度。");
        }

        String trimmedProxyHost = proxyHostText == null ? "" : proxyHostText.trim();
        int proxyPort = Math.max(0, proxyPortValue);
        if (useProxy) {
            if (trimmedProxyHost.isEmpty()) {
                throw new IllegalArgumentException("请填写代理主机地址。");
            }
            if (proxyPort <= 0) {
                throw new IllegalArgumentException("代理端口必须大于 0。");
            }
        }

        TileDownloadConfig config = TileDownloadConfig.builder()
            .baseDirectory(directory)
            .longitudeRange(startLon, endLon)
            .latitudeRange(startLat, endLat)
            .zoomRange(minZoom, maxZoom)
            .threads(threads)
            .downloadType(downloadType)
            .proxy(useProxy, trimmedProxyHost, proxyPort)
            .build();

        if (manualSelection) {
            if (selectedSources.isEmpty()) {
                throw new IllegalArgumentException("请选择至少一个地图源。");
            }
            Set<String> ids = new LinkedHashSet<>();
            for (TileSourceOption option : selectedSources) {
                ids.add(option.id());
            }
            config.setSelectedSourceIds(ids);
            config.setUseExplicitSources(true);
        } else {
            config.setSelectedSourceIds(Set.of());
            config.setUseExplicitSources(false);
        }

        return config;
    }

    private static double parseDouble(String text, String field) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " 输入格式错误。");
        }
    }

    private static void launchDownload(TileDownloadConfig config,
                                       JButton startButton,
                                       JProgressBar progressBar,
                                       JTextArea logArea) {
        startButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("下载中...");
        appendLog(logArea, "开始下载任务...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                try {
                    Files.createDirectories(config.getBaseDirectory());
                } catch (IOException ex) {
                    appendLog(logArea, "创建目录失败: " + ex.getMessage());
                    return null;
                }

                try (TextAreaOutputStream taos = new TextAreaOutputStream(logArea);
                     PrintStream redirect = new PrintStream(taos, true, StandardCharsets.UTF_8)) {
                    System.setOut(redirect);
                    System.setErr(redirect);
                    TileDownloader.run(config);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    appendLog(logArea, "下载被中断。");
                } catch (IOException ex) {
                    appendLog(logArea, "输出重定向失败: " + ex.getMessage());
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
                return null;
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setString("已结束");
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

    private static void addRow(JPanel panel, GridBagConstraints gbc, String labelText, java.awt.Component component) {
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(component, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
    }

    private static final class TextAreaOutputStream extends OutputStream implements AutoCloseable {
        private final JTextArea textArea;
        private final StringBuilder buffer = new StringBuilder();
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
                buffer.append((char) b);
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
            if (buffer.length() == 0 && !appendNewLine) {
                return;
            }
            String text = buffer.toString();
            buffer.setLength(0);
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

    private record TileSourceOption(String id, String name) {
        private String display() {
            return id + " - " + name;
        }
    }
}

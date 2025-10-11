package com.yuqiangdede;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable configuration container for tile download tasks.
 */
public final class TileDownloadConfig {

    private Path baseDirectory;
    private double startLon;
    private double startLat;
    private double endLon;
    private double endLat;
    private int minZoom;
    private int maxZoom;
    private int threads;
    private int startOffset;
    private int progressStep;
    private int largeProgressStep;
    private int bufferSize;
    private String downloadType;
    private final Set<String> selectedSourceIds = new LinkedHashSet<>();
    private boolean useExplicitSources;
    private boolean useProxy;
    private String proxyHost;
    private int proxyPort;

    private TileDownloadConfig(Builder builder) {
        this.baseDirectory = builder.baseDirectory;
        this.startLon = builder.startLon;
        this.startLat = builder.startLat;
        this.endLon = builder.endLon;
        this.endLat = builder.endLat;
        this.minZoom = builder.minZoom;
        this.maxZoom = builder.maxZoom;
        this.threads = builder.threads;
        this.startOffset = builder.startOffset;
        this.progressStep = builder.progressStep;
        this.largeProgressStep = builder.largeProgressStep;
        this.bufferSize = builder.bufferSize;
        this.downloadType = builder.downloadType;
        if (!builder.selectedSourceIds.isEmpty()) {
            this.selectedSourceIds.addAll(builder.selectedSourceIds);
            this.useExplicitSources = true;
        }
        this.useProxy = builder.useProxy;
        this.proxyHost = builder.proxyHost;
        this.proxyPort = builder.proxyPort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TileDownloadConfig defaults() {
        return builder().build();
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(Path baseDirectory) {
        this.baseDirectory = Objects.requireNonNull(baseDirectory, "baseDirectory");
    }

    public double getStartLon() {
        return startLon;
    }

    public void setStartLon(double startLon) {
        this.startLon = startLon;
    }

    public double getStartLat() {
        return startLat;
    }

    public void setStartLat(double startLat) {
        this.startLat = startLat;
    }

    public double getEndLon() {
        return endLon;
    }

    public void setEndLon(double endLon) {
        this.endLon = endLon;
    }

    public double getEndLat() {
        return endLat;
    }

    public void setEndLat(double endLat) {
        this.endLat = endLat;
    }

    public int getMinZoom() {
        return minZoom;
    }

    public void setMinZoom(int minZoom) {
        this.minZoom = minZoom;
    }

    public int getMaxZoom() {
        return maxZoom;
    }

    public void setMaxZoom(int maxZoom) {
        this.maxZoom = maxZoom;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    public int getProgressStep() {
        return progressStep;
    }

    public void setProgressStep(int progressStep) {
        this.progressStep = progressStep;
    }

    public int getLargeProgressStep() {
        return largeProgressStep;
    }

    public void setLargeProgressStep(int largeProgressStep) {
        this.largeProgressStep = largeProgressStep;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public String getDownloadType() {
        return downloadType;
    }

    public void setDownloadType(String downloadType) {
        this.downloadType = downloadType == null ? "all" : downloadType;
    }

    public Set<String> getSelectedSourceIds() {
        return Collections.unmodifiableSet(selectedSourceIds);
    }

    public void setSelectedSourceIds(Set<String> ids) {
        this.selectedSourceIds.clear();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    this.selectedSourceIds.add(id.trim());
                }
            }
        }
        this.useExplicitSources = !this.selectedSourceIds.isEmpty();
    }

    public boolean isUseExplicitSources() {
        return useExplicitSources;
    }

    public void setUseExplicitSources(boolean useExplicitSources) {
        this.useExplicitSources = useExplicitSources;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public static final class Builder {
        private Path baseDirectory = Paths.get("D:\\temp");
        private double startLon = 117;
        private double startLat = 38;
        private double endLon = 118;
        private double endLat = 39;
        private int minZoom = 0;
        private int maxZoom = 12;
        private int threads = 16;
        private int startOffset = 0;
        private int progressStep = 100;
        private int largeProgressStep = 10_000;
        private int bufferSize = 8 * 1024;
        private String downloadType = "all";
        private final Set<String> selectedSourceIds = new LinkedHashSet<>();
        private boolean useProxy;
        private String proxyHost = "";
        private int proxyPort = 0;

        private Builder() {
        }

        public Builder baseDirectory(Path value) {
            this.baseDirectory = Objects.requireNonNull(value, "baseDirectory");
            return this;
        }

        public Builder longitudeRange(double start, double end) {
            this.startLon = start;
            this.endLon = end;
            return this;
        }

        public Builder latitudeRange(double start, double end) {
            this.startLat = start;
            this.endLat = end;
            return this;
        }

        public Builder zoomRange(int min, int max) {
            this.minZoom = min;
            this.maxZoom = max;
            return this;
        }

        public Builder threads(int value) {
            this.threads = value;
            return this;
        }

        public Builder startOffset(int value) {
            this.startOffset = value;
            return this;
        }

        public Builder progressStep(int value) {
            this.progressStep = value;
            return this;
        }

        public Builder largeProgressStep(int value) {
            this.largeProgressStep = value;
            return this;
        }

        public Builder bufferSize(int value) {
            this.bufferSize = value;
            return this;
        }

        public Builder downloadType(String value) {
            this.downloadType = value == null ? "all" : value.trim();
            return this;
        }

        public Builder selectedSources(Set<String> ids) {
            this.selectedSourceIds.clear();
            if (ids != null) {
                this.selectedSourceIds.addAll(ids);
            }
            return this;
        }

        public Builder proxy(boolean enabled, String host, int port) {
            this.useProxy = enabled;
            this.proxyHost = host == null ? "" : host.trim();
            this.proxyPort = port;
            return this;
        }

        public TileDownloadConfig build() {
            return new TileDownloadConfig(this);
        }
    }
}

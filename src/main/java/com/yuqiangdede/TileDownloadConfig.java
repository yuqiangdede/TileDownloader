package com.yuqiangdede;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable configuration container for tile download tasks.
 */
@Getter
@Setter

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


    public static final class Builder {
        private Path baseDirectory = Paths.get("D:\\temp");
        private double startLon = 117;
        private double startLat = 38;
        private double endLon = 118;
        private double endLat = 39;
        private int minZoom = 0;
        private int maxZoom = 12;
        private int threads = 64;
        private final int startOffset = 0;
        private final int progressStep = 100;
        private final int largeProgressStep = 10_000;
        private final int bufferSize = 8 * 1024;
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


        public Builder downloadType(String value) {
            this.downloadType = value == null ? "all" : value.trim();
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

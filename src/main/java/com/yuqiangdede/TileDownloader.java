package com.yuqiangdede;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility to batch download map tiles for a given latitude/longitude range.
 */
public final class TileDownloader {

    private static final Path BASE_DIRECTORY = Paths.get("D:\\temp");

    private static final double START_LON = 117;
    private static final double START_LAT = 38;
    private static final double END_LON = 118;
    private static final double END_LAT = 39;

    private static final int MIN_ZOOM = 0;
    private static final int MAX_ZOOM = 12;
    private static final int THREADS = 16;
    private static final int START = 0;

    private static final int PROGRESS_STEP = 100;
    private static final int LARGE_PROGRESS_STEP = 10_000;
    private static final int BUFFER_SIZE = 8 * 1024;

    private static final String DOWNLOAD_TYPE = "all";

    private static final String[] GAODE_SUBDOMAINS = new String[] {"webst01", "webst02", "webst03", "webst04"};
    private static final String[] GOOGLE_SUBDOMAINS = new String[] {"mt0", "mt1", "mt2", "mt3"};
    private static final String[] OSM_FR_HOT_SUBDOMAINS = new String[] {"a", "b", "c"};
    private static final String[] CYCLOSM_SUBDOMAINS = new String[] {"a", "b", "c"};

    private static final TileSource[] TILE_SOURCES = new TileSource[] {
        new TileSource("osm-standard", "OSM Standard", "https://tile.openstreetmap.org/", "osm-standard", "ZXY"),
        osmHotSource(),
        cycloSmSource(),
        new TileSource("arcgis-topo", "ArcGIS Topographic", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/", "arcgis-topo", "ZYX"),
        new TileSource("arcgis-imagery", "ArcGIS Imagery", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/", "arcgis-imagery", "ZYX"),
        new TileSource("seamap-base", "OpenSeaMap Base", "https://t2.openseamap.org/tile/", "seamap-base", "ZXY"),
        new TileSource("seamap-seamark", "OpenSeaMap Seamarks", "https://tiles.openseamap.org/seamark/", "seamap-seamark", "ZXY"),
        gaodeSource("gaode-satellite", "Gaode Satellite", "6", "gaode-satellite", ".jpg"),
        gaodeSource("gaode-hybrid", "Gaode Hybrid", "7", "gaode-hybrid", ".png"),
        gaodeSource("gaode-roadnet", "Gaode Roadnet", "8", "gaode-roadnet", ".png"),
        gaodeSource("gaode-light", "Gaode Light High POI", "9", "gaode-light", ".png"),
        gaodeSource("gaode-light-poi", "Gaode Light Low POI", "10", "gaode-light-poi", ".png"),
        googleSource("google-vector", "Google Vector", "m", "google-vector", ".png"),
        googleSource("google-satellite", "Google Satellite", "s", "google-satellite", ".jpg"),
        googleSource("google-hybrid", "Google Hybrid", "y", "google-hybrid", ".jpg"),
        googleSource("google-terrain", "Google Terrain", "t", "google-terrain", ".png"),
        googleSource("google-terrain-labels", "Google Terrain Labels", "p", "google-terrain-labels", ".png"),
        googleSource("google-roads", "Google Roads Overlay", "h", "google-roads", ".png")
    };

    private static final Set<String> ALL_SOURCE_IDS = collectSourceIds(TILE_SOURCES);
    private static final Map<String, Set<String>> DOWNLOAD_TYPES = createDownloadTypes();
    private TileDownloader() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Application entry point.
     */
    public static void main(String[] args) throws InterruptedException {
        for (TileSource source : TILE_SOURCES) {
            if (!source.isSupported()) {
                System.err.printf("Unsupported tile service %s (%s)%n", source.name, source.baseUrl);
            }
        }
        SourceFilter filter = parseSourceFilter(args);
        if (!filter.unknownIds.isEmpty()) {
            System.err.printf("Unknown tile source ids: %s%n", String.join(", ", filter.unknownIds));
        }

        TileSource[] supportedSources = Arrays.stream(TILE_SOURCES)
            .filter(TileSource::isSupported)
            .toArray(TileSource[]::new);

        if (supportedSources.length == 0) {
            System.err.println("No supported tile service configured.");
            return;
        }

        Set<String> typeSelection = resolveDownloadType(DOWNLOAD_TYPE);

        TileSource[] activeSources = Arrays.stream(supportedSources)
            .filter(source -> {
                if (filter.enabled) {
                    return filter.ids.contains(source.id);
                }
                if (typeSelection.isEmpty()) {
                    return true;
                }
                return typeSelection.contains(source.id);
            })
            .toArray(TileSource[]::new);

        if (activeSources.length == 0) {
            if (filter.enabled) {
                System.err.println("No tile source matches the selection.");
                System.err.printf("Available ids: %s%n", formatSourceIds(supportedSources));
            } else {
                System.err.printf("Download type '%s' does not match any tile source.%n", DOWNLOAD_TYPE);
                System.err.printf("Available types: %s%n", formatTypeIds());
            }
            return;
        }

        if (filter.enabled) {
            System.out.printf("Selected sources: %s%n", formatSourceIds(activeSources));
        } else if (!typeSelection.isEmpty()) {
            System.out.printf("Download type '%s' sources: %s%n", DOWNLOAD_TYPE, formatSourceIds(activeSources));
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger completedTiles = new AtomicInteger(0);
        int tilesPerSource = calculateTotalTiles();
        int totalTiles = tilesPerSource * activeSources.length;

        for (TileSource source : activeSources) {
            for (int zoom = MIN_ZOOM; zoom <= MAX_ZOOM; zoom++) {
                int xMin = Math.min(lonToTileX(START_LON, zoom), lonToTileX(END_LON, zoom));
                int xMax = Math.max(lonToTileX(START_LON, zoom), lonToTileX(END_LON, zoom));
                int yMin = Math.min(latToTileY(START_LAT, zoom), latToTileY(END_LAT, zoom));
                int yMax = Math.max(latToTileY(START_LAT, zoom), latToTileY(END_LAT, zoom));

                for (int x = xMin; x <= xMax; x++) {
                    for (int y = yMin; y <= yMax; y++) {
                        final int tileZoom = zoom;
                        final int tileX = x;
                        final int tileY = y;
                        executor.submit(() -> processTile(source, completedTiles, totalTiles, tileZoom, tileX, tileY));
                    }
                }
            }
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    private static void processTile(TileSource source,
                                    AtomicInteger completedTiles,
                                    int totalTiles,
                                    int zoom,
                                    int x,
                                    int y) {
        String url = buildTileUrl(source, zoom, x, y);
        if (url.isEmpty()) {
            return;
        }

        int completed = completedTiles.incrementAndGet();
        if (completed <= START) {
            return;
        }

        try {
            String filePath = downloadTile(source, url, zoom, x, y);
            boolean shouldLog = completed % LARGE_PROGRESS_STEP == 0;
            if (filePath != null && completed % PROGRESS_STEP == 0) {
                shouldLog = true;
            }

            if (shouldLog) {
                String displayPath = filePath != null ? filePath : "-";
                System.out.printf("[%s] Completed %d/%d tiles, last file: %s%n", source.name, completed, totalTiles, displayPath);
            }
        } catch (IOException e) {
            System.out.printf("[%s] %s: download failed (%s)%n", source.name, url, e.getMessage());
        }
    }

    private static SourceFilter parseSourceFilter(String[] args) {
        Set<String> knownIds = new HashSet<>();
        for (TileSource source : TILE_SOURCES) {
            knownIds.add(source.id);
        }

        if (args == null || args.length == 0) {
            return SourceFilter.all();
        }

        Set<String> requested = new LinkedHashSet<>();
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String value = arg;
            if (value.startsWith("--sources=")) {
                value = value.substring("--sources=".length());
            }
            for (String token : value.split(",")) {
                String normalized = normalizeId(token);
                if (!normalized.isEmpty()) {
                    requested.add(normalized);
                }
            }
        }

        if (requested.isEmpty() || requested.contains("all")) {
            return SourceFilter.all();
        }

        Set<String> unknown = new LinkedHashSet<>();
        for (String id : requested) {
            if (!knownIds.contains(id)) {
                unknown.add(id);
            }
        }

        requested.removeAll(unknown);
        return new SourceFilter(true, requested, unknown);
    }

    private static String formatSourceIds(TileSource[] sources) {
        if (sources.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sources.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(sources[i].id);
        }
        return builder.toString();
    }

    private static String formatTypeIds() {
        Set<String> values = new LinkedHashSet<>();
        values.add("all");
        values.addAll(DOWNLOAD_TYPES.keySet());
        values.addAll(ALL_SOURCE_IDS);
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (String value : values) {
            if (index++ > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static Set<String> resolveDownloadType(String type) {
        Set<String> selection = new LinkedHashSet<>();
        if (type == null || type.isBlank()) {
            return selection;
        }

        Set<String> unknown = new LinkedHashSet<>();
        boolean allRequested = false;
        for (String token : type.split(",")) {
            String normalized = normalizeId(token);
            if (normalized.isEmpty()) {
                continue;
            }
            if ("all".equals(normalized)) {
                allRequested = true;
                break;
            }
            if (ALL_SOURCE_IDS.contains(normalized)) {
                selection.add(normalized);
                continue;
            }
            Set<String> preset = DOWNLOAD_TYPES.get(normalized);
            if (preset != null) {
                selection.addAll(preset);
                continue;
            }
            unknown.add(normalized);
        }

        if (allRequested) {
            return new LinkedHashSet<>();
        }

        if (!unknown.isEmpty()) {
            System.err.printf("Unknown download type(s): %s%n", String.join(", ", unknown));
            System.err.printf("Available types: %s%n", formatTypeIds());
        }

        selection.retainAll(ALL_SOURCE_IDS);
        return selection;
    }

    private static TileSource osmHotSource() {
        String template = "https://tile-{s}.openstreetmap.fr/hot/{z}/{x}/{y}.png";
        return new TileSource(
            "osm-hot",
            "OSM HOT",
            "https://tile.openstreetmap.fr/hot/",
            "osm-hot",
            "ZXY",
            template,
            OSM_FR_HOT_SUBDOMAINS,
            ".png"
        );
    }

    private static TileSource cycloSmSource() {
        String template = "https://{s}.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png";
        return new TileSource(
            "cyclosm",
            "CyclOSM",
            "https://tile-cyclosm.openstreetmap.fr/cyclosm/",
            "cyclosm",
            "ZXY",
            template,
            CYCLOSM_SUBDOMAINS,
            ".png"
        );
    }

    // Gaode style codes: 6=satellite, 7=hybrid, 8=roadnet, 9=light high POI, 10=light low POI.
    private static TileSource gaodeSource(String id, String name, String styleCode, String folderName, String fileExtension) {
        String template = "https://{s}.is.autonavi.com/appmaptile?style=" + styleCode + "&x={x}&y={y}&z={z}";
        return new TileSource(
            id,
            name,
            "https://webst01.is.autonavi.com/appmaptile",
            folderName,
            "ZXY",
            template,
            GAODE_SUBDOMAINS,
            fileExtension
        );
    }

    // Google layer codes: m=vector, s=satellite, y=satellite+labels, t=terrain, p=terrain+labels, h=roads overlay.
    private static TileSource googleSource(String id, String name, String layerCode, String folderName, String fileExtension) {
        String template = "https://{s}.google.com/vt/lyrs=" + layerCode + "&x={x}&y={y}&z={z}";
        return new TileSource(
            id,
            name,
            "https://mt0.google.com/vt",
            folderName,
            "ZXY",
            template,
            GOOGLE_SUBDOMAINS,
            fileExtension
        );
    }

    private static Set<String> collectSourceIds(TileSource[] sources) {
        Set<String> ids = new LinkedHashSet<>();
        for (TileSource source : sources) {
            ids.add(source.id);
        }
        return Collections.unmodifiableSet(ids);
    }

    private static Map<String, Set<String>> createDownloadTypes() {
        Map<String, Set<String>> types = new LinkedHashMap<>();
        registerType(types, "osm", "osm-standard", "osm-hot");
        registerType(types, "hot", "osm-hot");
        registerType(types, "cyclosm", "cyclosm");
        registerType(types, "arcgis", "arcgis-topo", "arcgis-imagery");
        registerType(types, "seamap", "seamap-base", "seamap-seamark");
        registerType(types, "gaode", "gaode-satellite", "gaode-hybrid", "gaode-roadnet", "gaode-light", "gaode-light-poi");
        registerType(types, "gaode-sat", "gaode-satellite");
        registerType(types, "google", "google-vector", "google-satellite", "google-hybrid", "google-terrain", "google-terrain-labels", "google-roads");
        registerType(types, "google-sat", "google-satellite", "google-hybrid");
        return types;
    }

    private static void registerType(Map<String, Set<String>> types, String typeId, String... sourceIds) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : sourceIds) {
            String normalizedId = normalizeId(id);
            if (!normalizedId.isEmpty()) {
                normalized.add(normalizedId);
            }
        }
        registerType(types, typeId, normalized);
    }

    private static void registerType(Map<String, Set<String>> types, String typeId, Set<String> sourceIds) {
        String normalizedType = normalizeId(typeId);
        if (normalizedType.isEmpty()) {
            return;
        }

        Set<String> normalizedSources = new LinkedHashSet<>();
        for (String id : sourceIds) {
            String normalizedId = normalizeId(id);
            if (!normalizedId.isEmpty()) {
                normalizedSources.add(normalizedId);
            }
        }

        normalizedSources.retainAll(ALL_SOURCE_IDS);
        if (!normalizedSources.isEmpty()) {
            types.put(normalizedType, Collections.unmodifiableSet(new LinkedHashSet<>(normalizedSources)));
        }
    }

    private static int calculateTotalTiles() {
        int total = 0;
        for (int zoom = MIN_ZOOM; zoom <= MAX_ZOOM; zoom++) {
            int xMin = Math.min(lonToTileX(START_LON, zoom), lonToTileX(END_LON, zoom));
            int xMax = Math.max(lonToTileX(START_LON, zoom), lonToTileX(END_LON, zoom));
            int yMin = Math.min(latToTileY(START_LAT, zoom), latToTileY(END_LAT, zoom));
            int yMax = Math.max(latToTileY(START_LAT, zoom), latToTileY(END_LAT, zoom));
            int tileCountX = xMax - xMin + 1;
            int tileCountY = yMax - yMin + 1;
            total += tileCountX * tileCountY;
        }
        return total;
    }

    private static String buildTileUrl(TileSource source, int zoom, int x, int y) {
        return source.buildUrl(zoom, x, y);
    }

    private static String downloadTile(TileSource source, String url, int zoom, int x, int y) throws IOException {
        Path tilePath = buildTilePath(source, zoom, x, y);

        if (Files.exists(tilePath) && Files.size(tilePath) > 0) {
            return null;
        }

        Files.createDirectories(tilePath.getParent());

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);

        int statusCode = connection.getResponseCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP status " + statusCode);
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = Files.newOutputStream(
                 tilePath,
                 StandardOpenOption.CREATE,
                 StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } finally {
            connection.disconnect();
        }

        return tilePath.toAbsolutePath().toString();
    }

    private static Path buildTilePath(TileSource source, int zoom, int x, int y) {
        return source.directory
            .resolve(String.valueOf(zoom))
            .resolve(String.valueOf(x))
            .resolve(source.fileNameForTile(y));
    }

    private static int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180) / 360 * (1 << zoom));
    }

    private static int latToTileY(double lat, int zoom) {
        double rad = Math.toRadians(lat);
        double value = (1 - Math.log(Math.tan(rad) + (1 / Math.cos(rad))) / Math.PI) / 2 * (1 << zoom);
        return (int) Math.floor(value);
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_-]+", "-");
        normalized = normalized.replace('_', '-');
        normalized = normalized.replaceAll("^-+", "");
        normalized = normalized.replaceAll("-+$", "");
        return normalized;
    }

    private static final class SourceFilter {
        private final boolean enabled;
        private final Set<String> ids;
        private final Set<String> unknownIds;

        private SourceFilter(boolean enabled, Set<String> ids, Set<String> unknownIds) {
            this.enabled = enabled;
            this.ids = ids;
            this.unknownIds = unknownIds;
        }

        private static SourceFilter all() {
            return new SourceFilter(false, new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    private static final class TileSource {
        private final String id;
        private final String name;
        private final String baseUrl;
        private final String mode;
        private final Path directory;
        private final String urlTemplate;
        private final String[] subdomains;
        private final AtomicInteger subdomainCounter = new AtomicInteger();
        private final String fileExtension;

        private TileSource(String id, String name, String baseUrl, String folderName, String modeOverride) {
            this(id, name, baseUrl, folderName, modeOverride, null, null, ".png");
        }

        private TileSource(String id, String name, String baseUrl, String folderName, String modeOverride, String template, String[] subdomains) {
            this(id, name, baseUrl, folderName, modeOverride, template, subdomains, ".png");
        }

        private TileSource(String id, String name, String baseUrl, String folderName, String modeOverride, String template, String[] subdomains, String fileExtension) {
            this.id = normalizeId(id);
            if (this.id.isEmpty()) {
                throw new IllegalArgumentException("Tile source id must not be empty");
            }
            this.name = name;
            this.baseUrl = baseUrl;
            this.mode = resolveMode(baseUrl, modeOverride);
            this.fileExtension = normalizeExtension(fileExtension);
            this.subdomains = prepareSubdomains(subdomains);
            this.directory = BASE_DIRECTORY.resolve(resolveFolderName(baseUrl, folderName, name, this.id));

            if (template != null && !template.isBlank()) {
                this.urlTemplate = template;
            } else {
                String normalizedBase = ensureTrailingSlash(baseUrl);
                this.urlTemplate = createDefaultTemplate(normalizedBase, this.mode);
            }
        }

        private String buildUrl(int zoom, int x, int y) {
            if (urlTemplate == null || urlTemplate.isEmpty()) {
                return "";
            }
            String result = urlTemplate;
            if (result.contains("{s}")) {
                result = result.replace("{s}", nextSubdomain());
            }
            return result
                .replace("{z}", Integer.toString(zoom))
                .replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y));
        }

        private String fileNameForTile(int y) {
            return y + fileExtension;
        }

        private String nextSubdomain() {
            if (subdomains.length == 0) {
                return "";
            }
            int index = Math.floorMod(subdomainCounter.getAndIncrement(), subdomains.length);
            return subdomains[index];
        }

        private boolean isSupported() {
            return urlTemplate != null && !urlTemplate.isEmpty();
        }

        private static String[] prepareSubdomains(String[] subdomains) {
            if (subdomains == null || subdomains.length == 0) {
                return new String[0];
            }
            return Arrays.copyOf(subdomains, subdomains.length);
        }

        private static String normalizeExtension(String extension) {
            if (extension == null || extension.isBlank()) {
                return ".png";
            }
            String trimmed = extension.trim();
            if (!trimmed.startsWith(".")) {
                trimmed = "." + trimmed;
            }
            return trimmed;
        }

        private static String ensureTrailingSlash(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            return value.endsWith("/") ? value : value + "/";
        }

        private static String createDefaultTemplate(String baseUrl, String mode) {
            if (mode == null || mode.isEmpty()) {
                return "";
            }
            if ("ZYX".equals(mode)) {
                return baseUrl + "{z}/{y}/{x}";
            }
            if ("ZXY".equals(mode)) {
                return baseUrl + "{z}/{x}/{y}.png";
            }
            return "";
        }

        private static String resolveMode(String baseUrl, String modeOverride) {
            if (modeOverride != null) {
                String normalized = modeOverride.trim().toUpperCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
            return detectMode(baseUrl);
        }

        private static String detectMode(String baseUrl) {
            if (baseUrl == null) {
                return "";
            }
            String lower = baseUrl.toLowerCase(Locale.ROOT);
            if (lower.contains("arcgis")) {
                return "ZYX";
            }
            if (lower.contains("openstreetmap") || lower.contains("openseamap")) {
                return "ZXY";
            }
            if (lower.contains("google") || lower.contains("autonavi")) {
                return "ZXY";
            }
            return "";
        }

        private static String resolveFolderName(String baseUrl, String folderName, String fallbackName, String fallbackId) {
            String sanitizedFolder = sanitize(folderName);
            if (sanitizedFolder != null) {
                return sanitizedFolder;
            }
            String sanitizedName = sanitize(fallbackName);
            if (sanitizedName != null) {
                return sanitizedName;
            }
            String sanitizedId = sanitize(fallbackId);
            if (sanitizedId != null) {
                return sanitizedId;
            }
            String sanitizedUrl = sanitize(baseUrl == null ? "" : baseUrl.replaceFirst("https?://", ""));
            return sanitizedUrl != null ? sanitizedUrl : "tiles";
        }

        private static String sanitize(String value) {
            if (value == null) {
                return null;
            }
            String sanitized = value.replaceAll("[^a-zA-Z0-9_-]+", "_");
            sanitized = sanitized.replaceAll("^_+", "");
            sanitized = sanitized.replaceAll("_+$", "");
            return sanitized.isEmpty() ? null : sanitized;
        }
    }
}

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

    private static final String BASE_URL = "http://a.tile-cyclosm.openstreetmap.fr/cyclosm/";
//    private static final String BASE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/";
//    private static final String BASE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/";
//    private static final String BASE_URL = "http://b.tile-cyclosm.openstreetmap.fr/cyclosm/";
    private TileDownloader() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Application entry point.
     */
    public static void main(String[] args) throws InterruptedException {
        String mode;
        if (BASE_URL.contains("ArcGIS")) {
            mode = "ZYX";
        } else if (BASE_URL.contains("openstreetmap")) {
            mode = "ZXY";
        } else {
            mode = "";
        }
        if (mode.isEmpty()) {
            System.err.printf("Unsupported tile service: %s%n", BASE_URL);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        AtomicInteger completedTiles = new AtomicInteger(0);
        int totalTiles = calculateTotalTiles();

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
                    executor.submit(() -> processTile(mode, completedTiles, totalTiles, tileZoom, tileX, tileY));
                }
            }
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    private static void processTile(String mode,
                                    AtomicInteger completedTiles,
                                    int totalTiles,
                                    int zoom,
                                    int x,
                                    int y) {
        String url = buildTileUrl(mode, zoom, x, y);
        if (url.isEmpty()) {
            return;
        }

        int completed = completedTiles.incrementAndGet();
        if (completed <= START) {
            return;
        }

        try {
            String filePath = downloadTile(url, zoom, x, y);
            boolean shouldLog = completed % LARGE_PROGRESS_STEP == 0;
            if (filePath != null && completed % PROGRESS_STEP == 0) {
                shouldLog = true;
            }

            if (shouldLog) {
                String displayPath = filePath != null ? filePath : "-";
                System.out.printf("Completed %d/%d tiles, last file: %s%n", completed, totalTiles, displayPath);
            }
        } catch (IOException e) {
            System.out.printf("%s: download failed (%s)%n", url, e.getMessage());
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

    private static String buildTileUrl(String mode, int zoom, int x, int y) {
        if ("ZYX".equals(mode)) {
            return BASE_URL + zoom + "/" + y + "/" + x;
        }
        if ("ZXY".equals(mode)) {
            return BASE_URL + zoom + "/" + x + "/" + y + ".png";
        }
        return "";
    }

    private static String downloadTile(String url, int zoom, int x, int y) throws IOException {
        Path tilePath = buildTilePath(zoom, x, y);

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

    private static Path buildTilePath(int zoom, int x, int y) {
        return BASE_DIRECTORY
            .resolve(String.valueOf(zoom))
            .resolve(String.valueOf(x))
            .resolve(y + ".png");
    }

    private static int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180) / 360 * (1 << zoom));
    }

    private static int latToTileY(double lat, int zoom) {
        double rad = Math.toRadians(lat);
        double value = (1 - Math.log(Math.tan(rad) + (1 / Math.cos(rad))) / Math.PI) / 2 * (1 << zoom);
        return (int) Math.floor(value);
    }
}

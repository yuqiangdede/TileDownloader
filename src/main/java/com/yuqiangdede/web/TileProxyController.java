package com.yuqiangdede.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


@RestController
public class TileProxyController {
    private final HttpClient http = HttpClient.newHttpClient();

    @GetMapping(value = "/tiles", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> proxy(
            @RequestParam int x, @RequestParam int y, @RequestParam int z,
            @RequestParam(defaultValue = "7") int style,
            @RequestParam(defaultValue = "webst02") String s) {

        String url = String.format("https://%s.is.autonavi.com/appmaptile?style=%d&x=%d&y=%d&z=%d",
                s, style, x, y, z);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(resp.body());
            }
            return ResponseEntity.status(resp.statusCode()).build();
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }
}

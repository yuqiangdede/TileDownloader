package com.yuqiangdede.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.*;
import java.net.URI;

@SpringBootApplication
public class MapApp {
    public static void main(String[] args) {
        SpringApplication.run(MapApp.class, args);
    }
    // 启动后自动打开浏览器
    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI("http://localhost:8080"));
            }
        } catch (Exception ignored) {
            System.out.println("无法打开浏览器");
        }
    }
}

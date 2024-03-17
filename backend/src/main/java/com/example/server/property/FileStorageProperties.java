package com.example.server.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {
    private String videoDir;

    private String thumbnailDir;

    public String getThumbnailDir() {
        return thumbnailDir;
    }

    public void setThumbnailDir(String thumbnailDir) {
        this.thumbnailDir = thumbnailDir;
    }

    public String getVideoDir() {
        return videoDir;
    }

    public void setVideoDir(String videoDir) {
        this.videoDir = videoDir;
    }
}

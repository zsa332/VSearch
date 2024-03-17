package com.example.server.dto;

import lombok.Data;

@Data
public class VideoRequest {
    private String fileId;
    private String fileName;
    private String tag;
    private boolean privacyStatus;
}

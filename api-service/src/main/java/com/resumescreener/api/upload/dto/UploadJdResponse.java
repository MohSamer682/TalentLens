package com.resumescreener.api.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadJdResponse {
    private String sessionId;
    private String jdFileKey;
    private String fileName;
    private long sizeBytes;
}

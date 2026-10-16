package com.resumescreener.api.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDownloadResponse {
    private String candidateId;
    private String fileName;
    private String url;
    private int expiresInMinutes;
}

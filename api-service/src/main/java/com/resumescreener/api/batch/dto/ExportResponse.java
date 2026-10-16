package com.resumescreener.api.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportResponse {
    private String sessionId;
    private String url;
    private int expiresInMinutes;
}

package com.resumescreener.api.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResumesResponse {
    private String sessionId;
    private int uploadedCount;
    private List<String> candidateIds;
}

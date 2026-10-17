package com.resumescreener.api.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.resumescreener.api.upload.ResumeIntake.RejectedFile;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResumesResponse {
    private String sessionId;
    private int uploadedCount;
    private List<String> candidateIds;
    /** Names of files skipped because the same file is already in the session */
    private List<String> duplicates;
    private List<RejectedFile> rejected;
}

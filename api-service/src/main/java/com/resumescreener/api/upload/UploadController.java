package com.resumescreener.api.upload;

import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.upload.dto.UploadJdResponse;
import com.resumescreener.api.upload.dto.UploadResumesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class UploadController {

    private static final long JD_MAX_BYTES = 5L * 1024 * 1024;
    private static final long RESUME_MAX_BYTES = 10L * 1024 * 1024;

    private final MinioUploadService uploadService;

    @PostMapping("/{id}/jd")
    public ResponseEntity<ApiResponse<UploadJdResponse>> uploadJd(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        requirePdf(file);
        requireUnder(file, JD_MAX_BYTES, "JD max size is 5MB");

        String key = uploadService.uploadJd(id, file, auth.getName());
        UploadJdResponse data = new UploadJdResponse(id, key, file.getOriginalFilename(), file.getSize());
        return ResponseEntity.ok(ApiResponse.success("JD uploaded successfully", data));
    }

    @PostMapping("/{id}/resumes")
    public ResponseEntity<ApiResponse<UploadResumesResponse>> uploadResumes(
            @PathVariable String id,
            @RequestParam("files") MultipartFile[] files,
            Authentication auth) {

        if (files == null || files.length == 0) {
            throw ApiException.validation("No resumes provided", "The files parameter was empty");
        }
        for (MultipartFile file : files) {
            requirePdf(file);
            requireUnder(file, RESUME_MAX_BYTES, "Resume max size is 10MB");
        }

        List<String> candidateIds = uploadService.uploadResumes(id, files, auth.getName());
        UploadResumesResponse data = new UploadResumesResponse(id, candidateIds.size(), candidateIds);
        return ResponseEntity.ok(ApiResponse.success("Resumes uploaded successfully", data));
    }

    private static void requirePdf(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_ERROR,
                    "Only PDF files are allowed", "Rejected file: " + file.getOriginalFilename());
        }
    }

    private static void requireUnder(MultipartFile file, long maxBytes, String limitDescription) {
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.VALIDATION_ERROR,
                    "File too large", limitDescription + ": " + file.getOriginalFilename());
        }
    }
}

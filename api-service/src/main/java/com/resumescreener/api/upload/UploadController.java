package com.resumescreener.api.upload;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiErrorCode;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.api.upload.dto.UploadJdResponse;
import com.resumescreener.api.upload.dto.UploadResumesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class UploadController {

    private static final long JD_MAX_BYTES = 5L * 1024 * 1024;

    private final MinioUploadService uploadService;
    private final AuditService auditService;

    @PostMapping("/{id}/jd")
    @PreAuthorize("hasAuthority('SESSION_RUN')")
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
    @PreAuthorize("hasAuthority('SESSION_RUN')")
    public ResponseEntity<ApiResponse<UploadResumesResponse>> uploadResumes(
            @PathVariable String id,
            @RequestParam("files") MultipartFile[] files,
            Authentication auth) {

        if (files == null || files.length == 0) {
            throw ApiException.validation("No resumes provided", "The files parameter was empty");
        }
        // Type and size are checked per file inside the service, so one bad file never sinks the batch
        UploadResumesResponse data = uploadService.uploadResumes(id, files, auth.getName());
        auditService.record("RESUME_UPLOAD", "session", id, "success",
                data.getUploadedCount() + " added, " + data.getDuplicates().size() + " duplicates, "
                        + data.getRejected().size() + " rejected");
        return ResponseEntity.ok(ApiResponse.success("Resumes uploaded", data));
    }

    @DeleteMapping("/{id}/candidates/{candidateId}")
    @PreAuthorize("hasAuthority('SESSION_RUN')")
    public ResponseEntity<ApiResponse<Void>> removeResume(
            @PathVariable String id,
            @PathVariable String candidateId,
            Authentication auth) {
        uploadService.removeResume(id, candidateId, auth.getName());
        auditService.record("RESUME_REMOVE", "candidate", candidateId, "success");
        return ResponseEntity.ok(ApiResponse.success("Resume removed", null));
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

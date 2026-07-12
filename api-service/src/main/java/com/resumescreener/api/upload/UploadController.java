package com.resumescreener.api.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class UploadController {

    private final MinioUploadService uploadService;

    @PostMapping("/{id}/jd")
    public ResponseEntity<String> uploadJd(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseEntity.badRequest().body("Only PDF files are allowed");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body("File too large (max 5MB)");
        }
        
        String key = uploadService.uploadJd(id, file, auth.getName());
        return ResponseEntity.ok(key);
    }

    @PostMapping("/{id}/resumes")
    public ResponseEntity<Object> uploadResumes(
            @PathVariable String id,
            @RequestParam("files") MultipartFile[] files,
            Authentication auth) {
        
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                return ResponseEntity.badRequest().body("Only PDF files are allowed: " + file.getOriginalFilename());
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File too large (max 10MB): " + file.getOriginalFilename());
            }
        }
        
        int count = uploadService.uploadResumes(id, files, auth.getName());
        return ResponseEntity.ok(count);
    }
}

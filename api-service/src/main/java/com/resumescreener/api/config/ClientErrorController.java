package com.resumescreener.api.config;

import com.resumescreener.api.common.ApiResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ClientErrorController {

    @PostMapping("/api/client-errors")
    public ResponseEntity<ApiResponse<Void>> reportClientError(@RequestBody ClientError error) {
        log.warn("Client error: code={} message={} url={} request={} stack={}",
                clean(error.getCode(), 64), clean(error.getMessage(), 300), clean(error.getUrl(), 300),
                clean(error.getRequest(), 500), clean(error.getStack(), 2000));
        return ResponseEntity.ok(ApiResponse.success("Error reported", null));
    }

    /** This endpoint is public and its text lands in the log: cap it and flatten line breaks so a report cannot forge log lines. */
    static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String flat = value.replaceAll("\\p{Cntrl}", " ");
        return flat.length() > max ? flat.substring(0, max) + "..." : flat;
    }

    @Data
    public static class ClientError {
        private String code;
        private String message;
        private String url;
        private String request;
        private String stack;
    }
}

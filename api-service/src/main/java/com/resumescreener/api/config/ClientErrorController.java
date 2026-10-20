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
                error.getCode(), error.getMessage(), error.getUrl(), error.getRequest(), error.getStack());
        return ResponseEntity.ok(ApiResponse.success("Error reported", null));
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

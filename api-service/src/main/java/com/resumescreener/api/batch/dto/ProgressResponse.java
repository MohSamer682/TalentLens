package com.resumescreener.api.batch.dto;

import lombok.Data;

@Data
public class ProgressResponse {
    private int total;
    private int processed;
    private int failed;
    private String status;
    private int percent;
}

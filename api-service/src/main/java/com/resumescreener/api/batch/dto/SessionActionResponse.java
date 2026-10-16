package com.resumescreener.api.batch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.resumescreener.common.enums.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionActionResponse {
    private String sessionId;
    private SessionStatus status;
    private String message;
}

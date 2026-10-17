package com.resumescreener.api.session.dto;

import com.resumescreener.common.session.SessionStatus;
import com.resumescreener.common.llm.LlmStatusDto;
import com.resumescreener.common.session.WeightsDto;
import lombok.Data;
import java.time.Instant;

@Data
public class SessionResponse {
    private String id;
    private String title;
    private String description;
    private SessionStatus status;
    private String jdFileKey;
    private Instant createdAt;
    private Instant completedAt;
    private WeightsDto weights;
    private Integer candidateCount;
    private String lastErrorMessage;
    private String reportFileKey;
    private Integer shortlistedCount;
    /** Set only when listing everyone's sessions */
    private String ownerName;
    /** Model the batch worker scores with and whether it is reachable; set on the session detail only */
    private LlmStatusDto llm;

    public SessionResponse(String id, String title, String description, SessionStatus status,
                           String jdFileKey, Instant createdAt, Instant completedAt, WeightsDto weights,
                           Integer candidateCount, String lastErrorMessage, String reportFileKey) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.jdFileKey = jdFileKey;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.weights = weights;
        this.candidateCount = candidateCount;
        this.lastErrorMessage = lastErrorMessage;
        this.reportFileKey = reportFileKey;
    }
}

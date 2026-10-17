package com.resumescreener.common.session;

public enum InterviewRecommendation {
    STRONG_HIRE,
    HIRE,
    NO_HIRE,
    STRONG_NO_HIRE;

    public boolean isHire() {
        return this == STRONG_HIRE || this == HIRE;
    }
}

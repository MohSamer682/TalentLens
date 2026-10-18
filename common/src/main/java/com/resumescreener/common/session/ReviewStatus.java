package com.resumescreener.common.session;

/** A recruiter's decision on a scored candidate, separate from processing {@link CandidateStatus}. */
public enum ReviewStatus {
    NONE,
    SHORTLISTED,
    CONTACTED,
    INTERVIEW_SCHEDULED,
    INTERVIEWED,
    OFFER,
    OFFER_APPROVED;

    public boolean isShortlistedOrBeyond() {
        return this != NONE;
    }
}

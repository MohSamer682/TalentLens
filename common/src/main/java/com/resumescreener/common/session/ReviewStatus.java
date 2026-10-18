package com.resumescreener.common.session;

/** A recruiter's decision on a scored candidate, separate from processing {@link CandidateStatus}. */
public enum ReviewStatus {
    NONE,
    SHORTLISTED,
    CONTACTED,
    INTERVIEW_SCHEDULED,
    INTERVIEWED,
    OFFER,
    OFFER_APPROVED,
    OFFER_SENT,
    ACCEPTED,
    DECLINED,
    JOINING_SET;

    public boolean isShortlistedOrBeyond() {
        return this != NONE;
    }
}

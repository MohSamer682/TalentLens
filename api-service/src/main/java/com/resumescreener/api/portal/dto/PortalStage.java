package com.resumescreener.api.portal.dto;

import com.resumescreener.common.session.ReviewStatus;

/** Where an application stands, in the candidate's words. An offer stays hidden until it is sent. */
public enum PortalStage {
    APPLIED, SHORTLISTED, INTERVIEWING, DECISION_PENDING, OFFER, ACCEPTED, JOINING, CLOSED;

    /** Exhaustive, so a new review status won't compile until it has a stage. */
    public static PortalStage of(ReviewStatus status) {
        return switch (status) {
            case NONE -> APPLIED;
            case SHORTLISTED, CONTACTED -> SHORTLISTED;
            case INTERVIEW_SCHEDULED -> INTERVIEWING;
            case INTERVIEWED, OFFER, OFFER_APPROVED -> DECISION_PENDING;
            case OFFER_SENT -> OFFER;
            case ACCEPTED -> ACCEPTED;
            case DECLINED -> CLOSED;
            case JOINING_SET -> JOINING;
        };
    }
}

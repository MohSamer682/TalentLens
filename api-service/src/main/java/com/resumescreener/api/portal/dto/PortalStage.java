package com.resumescreener.api.portal.dto;

import com.resumescreener.common.session.ReviewStatus;

import java.util.EnumMap;
import java.util.Map;

/** Where an application stands, in the candidate's words. An offer stays hidden until it is sent. */
public enum PortalStage {
    APPLIED, SHORTLISTED, INTERVIEWING, DECISION_PENDING, OFFER, ACCEPTED, JOINING, CLOSED;

    private static final Map<ReviewStatus, PortalStage> BY_REVIEW = new EnumMap<>(Map.ofEntries(
            Map.entry(ReviewStatus.NONE, APPLIED),
            Map.entry(ReviewStatus.SHORTLISTED, SHORTLISTED),
            Map.entry(ReviewStatus.CONTACTED, SHORTLISTED),
            Map.entry(ReviewStatus.INTERVIEW_SCHEDULED, INTERVIEWING),
            Map.entry(ReviewStatus.INTERVIEWED, DECISION_PENDING),
            Map.entry(ReviewStatus.OFFER, DECISION_PENDING),
            Map.entry(ReviewStatus.OFFER_APPROVED, DECISION_PENDING),
            Map.entry(ReviewStatus.OFFER_SENT, OFFER),
            Map.entry(ReviewStatus.ACCEPTED, ACCEPTED),
            Map.entry(ReviewStatus.DECLINED, CLOSED),
            Map.entry(ReviewStatus.JOINING_SET, JOINING)));

    public static PortalStage of(ReviewStatus status) {
        return BY_REVIEW.get(status);
    }
}

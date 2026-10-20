package com.resumescreener.api.portal.dto;

import com.resumescreener.common.session.InterviewKind;

/** One planned interview round and how far it has got. */
public record StageStep(InterviewKind kind, StepState state) {
}

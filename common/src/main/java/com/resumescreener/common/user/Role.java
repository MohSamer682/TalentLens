package com.resumescreener.common.user;

public enum Role {
    ADMIN,
    /** Sees recruiting analytics across all recruiters and all sessions */
    HR_CHIEF,
    RECRUITER,
    TECHNICAL_INTERVIEWER,
    HR_INTERVIEWER,
    /** Top management: sit on management interviews and see them on My Interviews */
    DEPARTMENT_DIRECTOR,
    CEO,
    CHAIRMAN;

    public boolean isTopManagement() {
        return this == DEPARTMENT_DIRECTOR || this == CEO || this == CHAIRMAN;
    }
}

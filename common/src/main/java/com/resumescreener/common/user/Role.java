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
    CHAIRMAN,
    /** A shortlisted applicant using the portal; never holds a permission */
    CANDIDATE;

    public enum Kind { STAFF, CANDIDATE }

    public Kind kind() {
        return this == CANDIDATE ? Kind.CANDIDATE : Kind.STAFF;
    }

    /** What the workflow relies on this role for. Fixed, because the code depends on it; admins can't edit it. */
    public java.util.List<String> uses() {
        return switch (this) {
            case ADMIN -> java.util.List.of("Always has every permission", "Manages users, roles and the audit log");
            case HR_CHIEF -> java.util.List.of("Gives the final offer approval", "Sees analytics across all recruiters");
            case RECRUITER -> java.util.List.of("Owns the screening cycles they start");
            case TECHNICAL_INTERVIEWER -> java.util.List.of("Can be picked for technical interviews");
            case HR_INTERVIEWER -> java.util.List.of("Can be picked for HR interviews");
            case DEPARTMENT_DIRECTOR -> java.util.List.of("Can be picked for management interviews", "Approves offers that need a director");
            case CEO, CHAIRMAN -> java.util.List.of("Can be picked for management interviews");
            case CANDIDATE -> java.util.List.of("Signs in to the candidate portal", "Never holds a permission");
        };
    }

    public boolean isTopManagement() {
        return this == DEPARTMENT_DIRECTOR || this == CEO || this == CHAIRMAN;
    }
}

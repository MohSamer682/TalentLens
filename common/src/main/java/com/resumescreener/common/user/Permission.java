package com.resumescreener.common.user;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** What a role may do. Granted per role in role_permissions; the three admin-only ones never leave ADMIN. */
public enum Permission {
    SESSION_RUN("Create sessions, upload resumes and start scoring", false),
    SESSION_VIEW_ALL("Open any recruiter's sessions", false),
    CANDIDATE_REVIEW("Shortlist and unshortlist candidates", false),
    PIPELINE_MANAGE("Contact candidates, schedule interviews and prepare offers", false),
    OFFER_APPROVE("Approve job offers", false),
    INTERVIEW_FEEDBACK("Give feedback on interviews assigned to them", false),
    ANALYTICS_VIEW("See recruiting analytics", false),
    USER_MANAGE("Assign roles to users", true),
    ROLE_MANAGE("Change what each role can do", true),
    AUDIT_VIEW("Read the audit log", true);

    private final String description;
    private final boolean adminOnly;

    Permission(String description, boolean adminOnly) {
        this.description = description;
        this.adminOnly = adminOnly;
    }

    public String description() {
        return description;
    }

    public boolean adminOnly() {
        return adminOnly;
    }

    /** ADMIN holds everything whatever the table says, so a bad edit can never lock admins out. */
    public static Set<Permission> effective(Role role, Collection<Permission> stored) {
        if (role == Role.ADMIN) {
            return EnumSet.allOf(Permission.class);
        }
        if (role == Role.CANDIDATE) {
            return EnumSet.noneOf(Permission.class);
        }
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        stored.stream().filter(p -> !p.adminOnly).forEach(result::add);
        return result;
    }
}

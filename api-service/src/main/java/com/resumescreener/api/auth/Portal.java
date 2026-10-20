package com.resumescreener.api.auth;

import com.resumescreener.common.user.Role;

/** The two sign-in doors. Each issues tokens only to its own kind of account, stamped with that kind as the audience. */
public enum Portal {
    STAFF("staff", "This is a candidate account. Sign in at /login."),
    CANDIDATE("candidate", "This is a staff account. Sign in at /admins.");

    private final String audience;
    private final String wrongDoor;

    Portal(String audience, String wrongDoor) {
        this.audience = audience;
        this.wrongDoor = wrongDoor;
    }

    public String audience() {
        return audience;
    }

    /** What someone with the right password but the wrong kind of account is told. */
    public String wrongDoor() {
        return wrongDoor;
    }

    public static Portal of(Role role) {
        return role.kind() == Role.Kind.CANDIDATE ? CANDIDATE : STAFF;
    }
}

package com.resumescreener.common.user;

/** HIDDEN accounts cannot sign in and drop out of pickers; their history stays. */
public enum UserStatus {
    ACTIVE,
    HIDDEN
}

package com.resumescreener.api.candidate.dto;

/** {@code changed} counts only candidates whose status actually changed. */
public record ReviewResult(int changed, long shortlistedTotal) {}

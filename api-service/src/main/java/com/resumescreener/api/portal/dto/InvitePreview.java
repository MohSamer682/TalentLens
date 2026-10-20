package com.resumescreener.api.portal.dto;

/** What the access-link page shows before a password is set: the job, a first name, and which form to offer. */
public record InvitePreview(String jobTitle, String candidateName, boolean accountExists) {
}

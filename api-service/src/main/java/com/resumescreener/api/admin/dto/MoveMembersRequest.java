package com.resumescreener.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MoveMembersRequest(@NotNull @Size(min = 1, max = 50) List<@NotBlank String> userIds) {}

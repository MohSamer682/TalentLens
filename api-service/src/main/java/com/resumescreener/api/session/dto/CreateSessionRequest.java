package com.resumescreener.api.session.dto;

import com.resumescreener.common.session.WeightsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSessionRequest {
    @NotBlank
    private String title;
    private String description;
    @Valid
    private WeightsDto weights;
}

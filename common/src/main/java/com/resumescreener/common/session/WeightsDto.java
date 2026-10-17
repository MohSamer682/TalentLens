package com.resumescreener.common.session;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeightsDto {
    @Min(0) @Max(100) private Double technicalSkills = 30.0;
    @Min(0) @Max(100) private Double experience = 25.0;
    @Min(0) @Max(100) private Double education = 15.0;
    @Min(0) @Max(100) private Double softSkills = 20.0;
    @Min(0) @Max(100) private Double cultureFit = 10.0;
}

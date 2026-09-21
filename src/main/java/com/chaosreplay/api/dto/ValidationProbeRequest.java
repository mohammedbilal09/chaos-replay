package com.chaosreplay.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ValidationProbeRequest(
    @NotBlank(message = "probeName must not be blank")
    @Size(min = 2, max = 50, message = "probeName must be between 2 and 50 characters")
    String probeName
) {
}


package com.user.management.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ZoneRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 255) String description,
        @Size(max = 120) String centre,
        Boolean active
) {
}

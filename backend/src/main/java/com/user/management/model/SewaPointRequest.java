package com.user.management.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SewaPointRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120) String name,
        Boolean active
) {
}

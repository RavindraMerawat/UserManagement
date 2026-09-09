package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Schema(example = "admin") String username,
        @NotBlank @Schema(example = "Admin@123") String password
) {
}

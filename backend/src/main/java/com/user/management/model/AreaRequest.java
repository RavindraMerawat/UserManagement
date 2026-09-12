package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Add or rename an area on the Setup screen. */
public record AreaRequest(
        @NotBlank(message = "Area name is required")
        @Size(max = 120)
        @Schema(description = "Area name", example = "Geeta Vihar")
        String name,

        @NotNull(message = "Zone is required")
        @Schema(description = "The zone this area belongs to")
        Long zoneId,

        Boolean active
) {
}

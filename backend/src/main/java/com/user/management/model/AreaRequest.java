package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AreaRequest(

        @NotBlank(message = "Area name is required")
        @Size(max = 120)
        @Schema(description = "Area name", example = "Geeta Vihar")
        String name,

        Boolean active
) {
}

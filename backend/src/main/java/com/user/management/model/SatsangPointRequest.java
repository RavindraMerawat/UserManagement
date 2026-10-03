package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SatsangPointRequest(

        @NotBlank(message = "Satsang point name is required")
        @Size(max = 120)
        @Schema(description = "Satsang point name", example = "Main Point")
        String name,

        Boolean active
) {
}

package com.user.management.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ZoneChangeCreateRequest(
        /** Optional for a SEWADAR login, which can only request a change for itself. */
        Long sewadarId,
        @NotNull(message = "Target zone is required") Long toZoneId,
        @Size(max = 500) String reason
) {
}

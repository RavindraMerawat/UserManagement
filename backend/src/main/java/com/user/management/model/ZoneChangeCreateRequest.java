package com.user.management.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ZoneChangeCreateRequest(
        /** Optional for a SEWADAR login, which can only request a change for itself. */
        Long sewadarId,

        @NotNull(message = "Target zone is required") Long toZoneId,

        /*
         * Required. Whoever reviews this is deciding whether to move a sewadar between
         * zones, and "why" is the whole of what they have to go on - a blank reason
         * made the approval a guess.
         */
        @NotBlank(message = "A reason is required - say why the zone should change")
        @Size(max = 500) String reason
) {
}

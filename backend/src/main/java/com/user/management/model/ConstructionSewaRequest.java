package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Record one day of construction sewa.
 *
 * <p>No count: a day either had construction sewa on it or it did not, so recording
 * the day is the whole entry and the count is how many days a sewadar has. Asking
 * for a figure as well invited two answers to the same question.</p>
 */
public record ConstructionSewaRequest(

        @NotNull(message = "Choose a sewadar")
        @Schema(description = "The sewadar who did the sewa")
        Long sewadarId,

        @NotNull(message = "The sewa date is required")
        @PastOrPresent(message = "The sewa date cannot be in the future")
        @Schema(description = "The day the sewa was done", example = "2026-09-26")
        LocalDate sewaDate,

        @Size(max = 300)
        String remarks
) {
}

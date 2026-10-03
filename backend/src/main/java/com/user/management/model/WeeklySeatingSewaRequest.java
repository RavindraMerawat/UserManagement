package com.user.management.model;

import com.user.management.entity.BadgeAction;
import com.user.management.entity.WeekDay;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Record one seating sewa and the token handed out for it. */
public record WeeklySeatingSewaRequest(

        @NotNull(message = "Choose a sewadar")
        Long sewadarId,

        @NotNull(message = "The sewa date is required")
        @PastOrPresent(message = "The sewa date cannot be in the future")
        @Schema(description = "The Sunday or Thursday the sewa was done", example = "2026-09-27")
        LocalDate sewaDate,

        @NotNull(message = "Choose Sunday or Thursday")
        @Schema(description = "Must agree with the day the date actually falls on")
        WeekDay weekDay,

        @NotBlank(message = "Badge No is required")
        @Size(max = 40)
        @Schema(description = "The seating token, shown on the screens as Badge No",
                example = "T-014")
        String tokenNo,

        @NotNull(message = "Choose issue or receive")
        @Schema(description = "ISSUE hands the badge out, RECEIVE takes it back. Both record "
                + "the seating and mark the day's attendance.")
        BadgeAction action
) {
}

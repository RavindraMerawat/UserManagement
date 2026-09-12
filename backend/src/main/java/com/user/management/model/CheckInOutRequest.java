package com.user.management.model;

import com.user.management.entity.SewaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Checks one sewadar in or out. */
public record CheckInOutRequest(

        @NotNull(message = "Sewadar is required") Long sewadarId,

        @Schema(description = "Defaults to ROSTER_SEWA when omitted")
        SewaType sewaType,

        @Schema(description = "Defaults to today. Cannot be a future date.")
        LocalDate attendanceDate,

        @Schema(description = "Defaults to the current server time")
        LocalTime time,

        @Size(max = 400) String remarks
) {
}

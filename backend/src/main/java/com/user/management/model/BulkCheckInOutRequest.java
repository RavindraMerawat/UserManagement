package com.user.management.model;

import com.user.management.entity.SewaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Checks a whole group in or out at once, used by the zone wise attendance screen.
 */
public record BulkCheckInOutRequest(

        @NotEmpty(message = "Select at least one sewadar")
        @Size(max = 500, message = "Up to 500 sewadars can be marked in one go")
        List<Long> sewadarIds,

        @Schema(description = "Defaults to DAILY_SEWA when omitted")
        SewaType sewaType,

        @Schema(description = "Defaults to today. Cannot be a future date.")
        LocalDate attendanceDate,

        @Schema(description = "Defaults to the current server time")
        LocalTime time,

        @Size(max = 400) String remarks
) {
}

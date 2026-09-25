package com.user.management.model;

import com.user.management.entity.SewaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Checks one sewadar in or out. */
@Schema(description = """
        Records one sewadar's arrival or departure.

        Leaving `attendanceDate` and `time` out stamps now, which is what the Mark
        Attendance screen does. Supplying both records a day that was missed - the
        Manage Past Attendance screen sends them, and holds itself to the current
        month's past days. The server's own rules are wider and are what actually
        bind: the date may not be in the future, a sewadar may not be checked in
        twice, and a check out must be later than its check in.
        """)
public record CheckInOutRequest(

        @NotNull(message = "Sewadar is required") Long sewadarId,

        @Schema(description = "Defaults to ROSTER_SEWA when omitted")
        SewaType sewaType,

        @Schema(description = "Defaults to today. Cannot be a future date.")
        LocalDate attendanceDate,

        @Schema(description = "Defaults to the current server time")
        LocalTime time,

        @Schema(description = "Free note kept with the day, shown in the attendance log. "
                + "Used for manual entries - \"biometric was down\", \"field duty\".",
                example = "Manual entry - biometric was down")
        @Size(max = 400) String remarks
) {
}

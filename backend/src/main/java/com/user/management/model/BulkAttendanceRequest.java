package com.user.management.model;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Marks a whole sewa sheet for one date and sewa type in a single call. */
public record BulkAttendanceRequest(
        @NotNull(message = "Date is required") LocalDate attendanceDate,
        @NotNull(message = "Sewa type is required") SewaType sewaType,
        LocalTime inTime,
        LocalTime outTime,
        @NotEmpty(message = "Add at least one sewadar") @Valid List<Entry> entries
) {
    public record Entry(
            @NotNull Long sewadarId,
            @NotNull AttendanceStatus status,
            LocalTime inTime,
            LocalTime outTime,
            String remarks
    ) {
    }
}

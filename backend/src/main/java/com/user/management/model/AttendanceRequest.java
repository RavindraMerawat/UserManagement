package com.user.management.model;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceRequest(
        @NotNull(message = "Sewadar is required") Long sewadarId,
        @NotNull(message = "Date is required") LocalDate attendanceDate,
        @NotNull(message = "Sewa type is required") SewaType sewaType,
        @NotNull(message = "Status is required") AttendanceStatus status,
        LocalTime inTime,
        LocalTime outTime,
        Double hours,
        @Size(max = 400) String remarks
) {
}

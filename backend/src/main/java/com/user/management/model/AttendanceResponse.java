package com.user.management.model;

import com.user.management.entity.Attendance;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceResponse(
        Long id,
        Long sewadarId,
        String badgeNumber,
        String sewadarName,
        Long zoneId,
        String zoneName,
        LocalDate attendanceDate,
        SewaType sewaType,
        String sewaTypeLabel,
        AttendanceStatus status,
        String statusLabel,
        LocalTime inTime,
        LocalTime outTime,
        Double hours,
        String remarks,
        String markedBy,
        Instant updatedAt
) {
    public static AttendanceResponse from(Attendance a) {
        return new AttendanceResponse(
                a.getId(),
                a.getSewadar().getId(),
                a.getSewadar().getBadgeNumber(),
                a.getSewadar().getName(),
                a.getZone().getId(),
                a.getZone().getName(),
                a.getAttendanceDate(),
                a.getSewaType(),
                a.getSewaType().getDisplayName(),
                a.getStatus(),
                a.getStatus().getDisplayName(),
                a.getInTime(),
                a.getOutTime(),
                a.getHours(),
                a.getRemarks(),
                a.getMarkedBy(),
                a.getUpdatedAt());
    }
}

package com.user.management.model;

import com.user.management.entity.Sewadar;
import com.user.management.entity.WeekDay;
import com.user.management.entity.WeeklySeatingSewa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** One seating sewa, with the sewadar as the screen lists them. */
public record WeeklySeatingSewaResponse(
        Long id,
        Long sewadarId,
        String badgeNumber,
        String name,
        String zoneName,
        String mobile,
        String area,
        String centerPoint,
        LocalDate sewaDate,
        WeekDay weekDay,
        String weekDayLabel,
        String tokenNo,
        /** What happened to the badge on this day. */
        boolean badgeIssued,
        boolean badgeReceived,
        LocalTime issuedAt,
        LocalTime receivedAt,
        Instant updatedAt,
        String updatedBy
) {

    public static WeeklySeatingSewaResponse from(WeeklySeatingSewa record) {
        Sewadar s = record.getSewadar();
        return new WeeklySeatingSewaResponse(
                record.getId(),
                s.getId(),
                s.getBadgeNumber(),
                s.getName(),
                s.getZone() == null ? null : s.getZone().getName(),
                s.getMobile(),
                s.getArea(),
                s.getCenterPoint(),
                record.getSewaDate(),
                record.getWeekDay(),
                record.getWeekDay().getDisplayName(),
                record.getTokenNo(),
                record.isBadgeIssued(),
                record.isBadgeReceived(),
                record.getIssuedAt(),
                record.getReceivedAt(),
                record.getUpdatedAt(),
                record.getUpdatedBy());
    }
}

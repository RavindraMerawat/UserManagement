package com.user.management.model;

import com.user.management.entity.Sewadar;
import com.user.management.entity.WeeklySeatingSewa;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;

/** One person behind a seating day count, as the grid lists them. */
public record SeatingPersonResponse(
        Long sewadarId,
        String badgeNumber,
        String name,
        String mobile,
        Integer age,
        String zoneName,
        String area,
        String tokenNo,

        /*
         * The day's check in and check out, as this desk recorded them.
         *
         * They are the seating record's own `issuedAt` and `receivedAt`: handing the
         * badge over is the check in and taking it back is the check out, and the
         * same two times are what the service writes to the day's attendance. Read
         * from here rather than from the attendance table on purpose - this grid is
         * the weekly seating's own list, and a person who was also marked present
         * for some other sewa that day should not have that time appear in it.
         */
        LocalTime checkInTime,
        LocalTime checkOutTime) {

    public static SeatingPersonResponse from(WeeklySeatingSewa record) {
        Sewadar s = record.getSewadar();
        return new SeatingPersonResponse(
                s.getId(),
                s.getBadgeNumber(),
                s.getName(),
                s.getMobile(),
                ageOf(s),
                s.getZone() == null ? null : s.getZone().getName(),
                s.getArea(),
                record.getTokenNo(),
                record.getIssuedAt(),
                record.getReceivedAt());
    }

    /** The age as recorded, or worked out from the birth date when only that is known. */
    private static Integer ageOf(Sewadar s) {
        if (s.getAge() != null) {
            return s.getAge();
        }
        LocalDate born = s.getDateOfBirth();
        return born == null ? null : Period.between(born, LocalDate.now()).getYears();
    }
}

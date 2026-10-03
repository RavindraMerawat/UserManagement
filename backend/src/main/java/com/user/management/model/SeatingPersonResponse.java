package com.user.management.model;

import com.user.management.entity.Sewadar;
import com.user.management.entity.WeeklySeatingSewa;

import java.time.LocalDate;
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
        String tokenNo) {

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
                record.getTokenNo());
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

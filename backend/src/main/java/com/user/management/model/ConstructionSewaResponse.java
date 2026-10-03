package com.user.management.model;

import com.user.management.entity.ConstructionSewa;
import com.user.management.entity.Sewadar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

/** One recorded day, with the sewadar as the screen lists them. */
public record ConstructionSewaResponse(
        Long id,
        Long sewadarId,
        String badgeNumber,
        String name,
        String zoneName,
        Integer age,
        String mobile,
        String area,
        String centerPoint,
        LocalDate sewaDate,
        String remarks,
        Instant updatedAt,
        String updatedBy
) {

    public static ConstructionSewaResponse from(ConstructionSewa record) {
        Sewadar s = record.getSewadar();
        return new ConstructionSewaResponse(
                record.getId(),
                s.getId(),
                s.getBadgeNumber(),
                s.getName(),
                s.getZone() == null ? null : s.getZone().getName(),
                ageOf(s),
                s.getMobile(),
                s.getArea(),
                s.getCenterPoint(),
                record.getSewaDate(),
                record.getRemarks(),
                record.getUpdatedAt(),
                record.getUpdatedBy());
    }

    /**
     * The age as recorded, or worked out from the birth date when only that is known.
     * Written down wins: it is what the office wrote on the slip.
     */
    private static Integer ageOf(Sewadar s) {
        if (s.getAge() != null) {
            return s.getAge();
        }
        LocalDate born = s.getDateOfBirth();
        return born == null ? null : Period.between(born, LocalDate.now()).getYears();
    }
}

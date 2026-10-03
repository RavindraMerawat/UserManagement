package com.user.management.model;

import com.user.management.entity.SewadarStatus;

public record MonthlyReportRow(
        Long sewadarId,
        String badgeNumber,
        String sewadarName,
        String zoneName,
        String area,
        String satsangPoint,
        String department,
        /** The grouping inside the area, printed as the ZONE column of the hours sheet. */
        String grouping,
        /** The designation, which decides where the row sits on that sheet. */
        String designation,
        /** Permanent or Open, which decides which of the zone's two tables it is in. */
        SewadarStatus status,
        /** Years old at the end of the reporting period, or null with no birth date. */
        Integer age,
        boolean exempted,
        long totalRecords,
        long presentDays,
        long halfDays,
        long leaveDays,
        long absentDays,
        long dailySewaDays,
        long constructionSewaDays,
        /** Check in to check out, added up over the month. 8.75 is 8 hours 45. */
        double totalHours,
        /**
         * The same time rounded to the nearest whole hour: 1:20 counts as 1, 1:38
         * counts as 2. It is what the office puts against a name when hours are
         * being tallied by hand, and half an hour is the line it rounds at.
         */
        long effectiveHours,
        /** Present + half day weighted, e.g. 12 present + 2 half days = 13.0 */
        double effectiveDays,
        /** effectiveDays / working days in the period, as a percentage. */
        double attendancePercent
) {
}

package com.user.management.model;

public record MonthlyReportRow(
        Long sewadarId,
        String badgeNumber,
        String sewadarName,
        String zoneName,
        String department,
        long totalRecords,
        long presentDays,
        long halfDays,
        long leaveDays,
        long absentDays,
        long rosterSewaDays,
        long constructionSewaDays,
        double totalHours,
        /** Present + half day weighted, e.g. 12 present + 2 half days = 13.0 */
        double effectiveDays,
        /** effectiveDays / working days in the period, as a percentage. */
        double attendancePercent
) {
}

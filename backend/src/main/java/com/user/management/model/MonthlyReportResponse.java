package com.user.management.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record MonthlyReportResponse(
        String title,
        LocalDate fromDate,
        LocalDate toDate,
        int daysInPeriod,
        String zoneName,
        String sewaTypeLabel,
        List<MonthlyReportRow> rows,
        Totals totals,
        Map<String, Long> statusBreakdown,
        Map<String, Long> sewaTypeBreakdown
) {
    public record Totals(
            int sewadarCount,
            long presentDays,
            long halfDays,
            long leaveDays,
            long absentDays,
            long rosterSewaDays,
            long constructionSewaDays,
            double totalHours,
            double averageAttendancePercent
    ) {
    }
}

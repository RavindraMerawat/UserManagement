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
            long dailySewaDays,
            long constructionSewaDays,
            double totalHours,
            /**
             * The rows' effective hours added up - each rounded first, so the column
             * on screen adds to the figure under it.
             */
            long effectiveHours,
            /** The rows' effective days added up. */
            double effectiveDays,
            double averageAttendancePercent
    ) {
    }
}

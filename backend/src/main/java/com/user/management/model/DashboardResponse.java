package com.user.management.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        String greetingName,
        String role,
        String roleDisplayName,
        String scopeLabel,
        LocalDate today,
        long totalSewadars,
        long presentToday,
        long absentToday,
        long leaveToday,
        long pendingRequests,

        /*
         * The same four figures for the period before, so the tiles can show real
         * movement. The UI does the arithmetic; the server sends what it measured.
         * Nothing here is estimated - if a comparison cannot be computed it comes
         * back as the same number, and the tile shows no trend rather than a
         * made-up one.
         */
        long totalSewadarsLastMonth,
        long presentYesterday,
        long absentYesterday,
        long leaveYesterday,

        /*
         * The other two tiles the design shows. activeUsers is zero for a role that
         * may not see accounts at all, and the dashboard shows that role a different
         * fourth tile rather than an empty one.
         */
        long pendingRequestsLastWeek,
        long activeUsers,
        long activeUsersLastMonth,
        long myMonthPresentDays,
        double myMonthHours,
        /** Present and absent days per month of this year, for the overview chart. */
        List<MonthlyAttendancePoint> monthlyAttendance,

        Map<String, Long> monthStatusBreakdown,
        Map<String, Long> monthSewaTypeBreakdown,
        List<AttendanceResponse> recentAttendance
) {
    /** One column pair on the Attendance Overview chart. */
    public record MonthlyAttendancePoint(int month, String label, long present, long absent) {
    }
}

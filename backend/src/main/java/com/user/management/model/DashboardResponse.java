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
        /*
         * The same three figures split male and female, which is what the dashboard
         * draws as separate cards. They are carried beside the totals rather than
         * replacing them: a total is not always male + female, because a record can
         * have no gender on it, and a card that quietly lost those people would be
         * worse than one that never claimed to hold them.
         *
         * Present and absent carry yesterday's split too, so those cards can show
         * movement. The totals do not: the register a month ago would need its own
         * historical query per gender, and a tile with no trend is honest where an
         * invented one would not be.
         */
        GenderSplit totalByGender,
        GenderSplit presentByGender,
        GenderSplit absentByGender,
        GenderSplit presentByGenderYesterday,
        GenderSplit absentByGenderYesterday,
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

    /** A count split by gender. `other` covers OTHER and records with none. */
    public record GenderSplit(long male, long female, long other) {
    }
}

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
        long pendingRequests,
        long myMonthPresentDays,
        double myMonthHours,
        Map<String, Long> monthStatusBreakdown,
        Map<String, Long> monthSewaTypeBreakdown,
        List<AttendanceResponse> recentAttendance
) {
}

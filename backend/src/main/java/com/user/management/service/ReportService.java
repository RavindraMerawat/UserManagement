package com.user.management.service;

import com.user.management.entity.SewaType;
import com.user.management.exception.BadRequestException;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import com.user.management.model.ZoneResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.projection.MonthlySummaryRow;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the attendance reports: a monthly report, a roster sewa report and a
 * construction sewa report are all the same aggregation with a different sewa filter.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final AttendanceRepository attendanceRepository;
    private final ZoneService zoneService;
    private final CurrentUserService currentUser;

    /** Monthly report for a given year/month. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(int year, int month, Long zoneId, Long sewadarId, SewaType sewaType) {
        if (month < 1 || month > 12) {
            throw new BadRequestException("Month must be between 1 and 12");
        }
        YearMonth ym = YearMonth.of(year, month);
        return range(ym.atDay(1), ym.atEndOfMonth(), zoneId, sewadarId, sewaType,
                "Monthly Attendance Report - " + ym.format(MONTH_TITLE));
    }

    /** Report over an arbitrary date range. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse range(LocalDate from,
                                       LocalDate to,
                                       Long zoneId,
                                       Long sewadarId,
                                       SewaType sewaType,
                                       String title) {
        if (from == null || to == null) {
            throw new BadRequestException("Both fromDate and toDate are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("toDate cannot be before fromDate");
        }
        if (ChronoUnit.DAYS.between(from, to) > 400) {
            throw new BadRequestException("Report range cannot exceed 400 days");
        }

        DataScope scope = currentUser.scope();
        if (zoneId != null) {
            currentUser.requireZoneAccess(zoneId);
        }
        if (sewadarId != null && !scope.allowsSewadar(sewadarId)) {
            throw new BadRequestException("You can only report on your own attendance");
        }

        List<MonthlySummaryRow> raw = attendanceRepository.monthlySummary(
                from, to, sewadarId, zoneId, sewaType, scope.zoneIds(), scope.sewadarId());

        int daysInPeriod = (int) ChronoUnit.DAYS.between(from, to) + 1;
        List<MonthlyReportRow> rows = raw.stream().map(r -> toRow(r, daysInPeriod)).toList();

        Map<String, Long> statusBreakdown = attendanceRepository
                .countByStatus(from, to, scope.zoneIds(), scope.sewadarId()).stream()
                .collect(Collectors.toMap(r -> r.getStatus().getDisplayName(), r -> r.getCount(),
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Long> sewaTypeBreakdown = attendanceRepository
                .countBySewaType(from, to, scope.zoneIds(), scope.sewadarId()).stream()
                .collect(Collectors.toMap(r -> r.getSewaType().getDisplayName(), r -> r.getCount(),
                        (a, b) -> a, LinkedHashMap::new));

        String zoneName = zoneId == null ? scopeLabel(scope) : zoneService.get(zoneId).name();

        return new MonthlyReportResponse(
                title == null ? defaultTitle(from, to, sewaType) : title,
                from,
                to,
                daysInPeriod,
                zoneName,
                sewaType == null ? "All Sewa Types" : sewaType.getDisplayName(),
                rows,
                totalsOf(rows),
                statusBreakdown,
                sewaTypeBreakdown);
    }

    /** Roster sewa report for a month. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse rosterSewa(int year, int month, Long zoneId, Long sewadarId) {
        YearMonth ym = YearMonth.of(year, month);
        return range(ym.atDay(1), ym.atEndOfMonth(), zoneId, sewadarId, SewaType.ROSTER_SEWA,
                "Roster Sewa Report - " + ym.format(MONTH_TITLE));
    }

    /** Construction sewa report for a month. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse constructionSewa(int year, int month, Long zoneId, Long sewadarId) {
        YearMonth ym = YearMonth.of(year, month);
        return range(ym.atDay(1), ym.atEndOfMonth(), zoneId, sewadarId, SewaType.CONSTRUCTION_SEWA,
                "Construction Sewa Report - " + ym.format(MONTH_TITLE));
    }

    private MonthlyReportRow toRow(MonthlySummaryRow r, int daysInPeriod) {
        long present = nz(r.getPresentDays());
        long half = nz(r.getHalfDays());
        double effective = present + (half * 0.5);
        double percent = daysInPeriod == 0 ? 0.0 : round(effective * 100.0 / daysInPeriod);

        return new MonthlyReportRow(
                r.getSewadarId(),
                r.getBadgeNumber(),
                r.getSewadarName(),
                r.getZoneName(),
                r.getDepartment(),
                nz(r.getTotalRecords()),
                present,
                half,
                nz(r.getLeaveDays()),
                nz(r.getAbsentDays()),
                nz(r.getRosterSewaDays()),
                nz(r.getConstructionSewaDays()),
                round(r.getTotalHours() == null ? 0.0 : r.getTotalHours()),
                round(effective),
                percent);
    }

    private MonthlyReportResponse.Totals totalsOf(List<MonthlyReportRow> rows) {
        double avgPercent = rows.isEmpty() ? 0.0 : round(rows.stream()
                .mapToDouble(MonthlyReportRow::attendancePercent).average().orElse(0.0));
        return new MonthlyReportResponse.Totals(
                rows.size(),
                sum(rows, MonthlyReportRow::presentDays),
                sum(rows, MonthlyReportRow::halfDays),
                sum(rows, MonthlyReportRow::leaveDays),
                sum(rows, MonthlyReportRow::absentDays),
                sum(rows, MonthlyReportRow::rosterSewaDays),
                sum(rows, MonthlyReportRow::constructionSewaDays),
                round(rows.stream().mapToDouble(MonthlyReportRow::totalHours).sum()),
                avgPercent);
    }

    private long sum(List<MonthlyReportRow> rows, Function<MonthlyReportRow, Long> field) {
        return rows.stream().mapToLong(r -> field.apply(r)).sum();
    }

    private String scopeLabel(DataScope scope) {
        if (scope.isGlobal()) {
            return "All Zones";
        }
        if (scope.sewadarId() != null) {
            return "My Records";
        }
        List<ZoneResponse> zones = zoneService.listAccessible(true);
        return zones.isEmpty() ? "My Zones" : zones.stream().map(ZoneResponse::name)
                .collect(Collectors.joining(", "));
    }

    private String defaultTitle(LocalDate from, LocalDate to, SewaType sewaType) {
        String prefix = sewaType == null ? "Attendance Report" : sewaType.getDisplayName() + " Report";
        return prefix + " - " + from + " to " + to;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

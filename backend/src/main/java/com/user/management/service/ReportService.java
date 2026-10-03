package com.user.management.service;

import com.user.management.entity.Locality;
import com.user.management.entity.SewaType;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import com.user.management.model.ZoneResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRoleRepository;
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
    private final SewadarRoleRepository sewadarRoleRepository;
    private final ZoneService zoneService;
    private final CurrentUserService currentUser;

    /** Monthly report for a given year/month. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(int year, int month, Long zoneId, Long sewadarId,
                                         SewaType sewaType, Long designationId, Locality locality) {
        if (month < 1 || month > 12) {
            throw new BadRequestException("Month must be between 1 and 12");
        }
        /*
         * The whole-register report is the office's; one person's hours are not.
         *
         * Two kinds of caller are therefore let through whatever their designation:
         * one that names a sewadar - the Sewadar Monthly Hours tab, or a
         * Co-ordinator looking somebody up in their zone - and a Sewadar login,
         * whose scope is already themselves and who gets a one-row report however
         * they ask for it. What is refused is the register at large.
         */
        if (sewadarId == null
                && currentUser.scope().sewadarId() == null
                && !currentUser.canViewMonthlyReport()) {
            throw new ForbiddenException(
                    "The Monthly Report is available to Admin, Office Incharge and Office Sewadar");
        }
        YearMonth ym = YearMonth.of(year, month);
        /*
         * The designation goes in the title, not only in the filter. The title is
         * what the screen shows and what the downloaded file is named after, so a
         * sheet of co-ordinators says so on it rather than looking like the whole
         * register with most of it missing.
         */
        String title = "Monthly Attendance Report - " + ym.format(MONTH_TITLE);
        String designation = designationName(designationId);
        if (designation != null) {
            title = title + " - " + designation;
        }
        if (locality != null) {
            title = title + " - " + locality.getDisplayName();
        }
        return range(ym.atDay(1), ym.atEndOfMonth(), zoneId, sewadarId, sewaType, designationId,
                locality, title);
    }

    /** Report over an arbitrary date range. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse range(LocalDate from,
                                       LocalDate to,
                                       Long zoneId,
                                       Long sewadarId,
                                       SewaType sewaType,
                                       Long designationId,
                                       Locality locality,
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
                from, to, sewadarId, zoneId, sewaType, designationId, locality,
                scope.zoneIds(), scope.gender(), scope.sewadarId());

        int daysInPeriod = (int) ChronoUnit.DAYS.between(from, to) + 1;
        List<MonthlyReportRow> rows = raw.stream().map(r -> toRow(r, daysInPeriod, to)).toList();

        Map<String, Long> statusBreakdown = attendanceRepository
                .countByStatus(from, to, scope.zoneIds(), scope.gender(), scope.sewadarId()).stream()
                .collect(Collectors.toMap(r -> r.getStatus().getDisplayName(), r -> r.getCount(),
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Long> sewaTypeBreakdown = attendanceRepository
                .countBySewaType(from, to, scope.zoneIds(), scope.gender(), scope.sewadarId()).stream()
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

    /** Daily sewa report for a month. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse dailySewa(int year, int month, Long zoneId, Long sewadarId) {
        YearMonth ym = YearMonth.of(year, month);
        return range(ym.atDay(1), ym.atEndOfMonth(), zoneId, sewadarId, SewaType.DAILY_SEWA, null, null,
                "Daily Sewa Report - " + ym.format(MONTH_TITLE));
    }

    /** Construction sewa report for a month. */
    @Transactional(readOnly = true)
    public MonthlyReportResponse constructionSewa(int year, int month, Long zoneId, Long sewadarId) {
        YearMonth ym = YearMonth.of(year, month);
        return range(ym.atDay(1), ym.atEndOfMonth(), zoneId, sewadarId, SewaType.CONSTRUCTION_SEWA, null, null,
                "Construction Sewa Report - " + ym.format(MONTH_TITLE));
    }

    /**
     * The designation's name, and a refusal if there is no such designation.
     *
     * <p>An id that matches nothing would otherwise come back as an empty report,
     * which reads like "nobody holds this designation" rather than "that filter was
     * wrong". The two are worth telling apart.</p>
     */
    private String designationName(Long designationId) {
        if (designationId == null) {
            return null;
        }
        return sewadarRoleRepository.findById(designationId)
                .orElseThrow(() -> new BadRequestException("No designation with id " + designationId))
                .getName();
    }

    private MonthlyReportRow toRow(MonthlySummaryRow r, int daysInPeriod, LocalDate asAt) {
        long present = nz(r.getPresentDays());
        long half = nz(r.getHalfDays());
        double effective = present + (half * 0.5);
        double percent = daysInPeriod == 0 ? 0.0 : round(effective * 100.0 / daysInPeriod);

        double hours = r.getTotalHours() == null ? 0.0 : r.getTotalHours();
        return new MonthlyReportRow(
                r.getSewadarId(),
                r.getBadgeNumber(),
                r.getSewadarName(),
                r.getZoneName(),
                r.getArea(),
                r.getSatsangPoint(),
                r.getDepartment(),
                r.getGroupingName(),
                r.getDesignation(),
                r.getStatus(),
                ageAt(r.getBirthDate(), asAt),
                Boolean.TRUE.equals(r.getExempted()),
                nz(r.getTotalRecords()),
                present,
                half,
                nz(r.getLeaveDays()),
                nz(r.getAbsentDays()),
                nz(r.getDailySewaDays()),
                nz(r.getConstructionSewaDays()),
                round(hours),
                Math.round(hours),
                round(effective),
                percent);
    }

    /**
     * Age in whole years at the end of the period the report covers, so a report
     * re-run next year does not silently age everyone in it.
     */
    private Integer ageAt(LocalDate birthDate, LocalDate asAt) {
        if (birthDate == null) {
            return null;
        }
        return java.time.Period.between(birthDate, asAt).getYears();
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
                sum(rows, MonthlyReportRow::dailySewaDays),
                sum(rows, MonthlyReportRow::constructionSewaDays),
                round(rows.stream().mapToDouble(MonthlyReportRow::totalHours).sum()),
                // The rounded values added, not the total rounded: the column on
                // screen has to add up to the line under it.
                rows.stream().mapToLong(MonthlyReportRow::effectiveHours).sum(),
                round(rows.stream().mapToDouble(MonthlyReportRow::effectiveDays).sum()),
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

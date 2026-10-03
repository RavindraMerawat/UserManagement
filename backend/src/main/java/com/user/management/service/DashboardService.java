package com.user.management.service;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Locality;
import com.user.management.repository.projection.GenderCount;
import com.user.management.repository.projection.LocalityGenderCount;
import com.user.management.entity.Gender;
import com.user.management.entity.RequestStatus;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.DashboardResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneChangeRequestRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Numbers behind the Home screen, all narrowed to the caller's data scope. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SewadarRepository sewadarRepository;
    private final AttendanceRepository attendanceRepository;
    private final ZoneChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUser;
    private final ZoneService zoneService;

    @Transactional(readOnly = true)
    public DashboardResponse load() {
        AppUserPrincipal principal = currentUser.principal();
        DataScope scope = currentUser.scope();

        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        // Every tile counts people, through the same query the grid behind it lists,
        // so tapping a tile can never show a different number of rows than the tile
        // just showed.
        long totalSewadars = sewadarRepository.countForDashboard(
                null, today, scope.zoneIds(), scope.gender(), scope.sewadarId());
        long presentToday = sewadarRepository.countForDashboard(
                AttendanceStatus.PRESENT, today, scope.zoneIds(), scope.gender(), scope.sewadarId());
        long absentToday = sewadarRepository.countForDashboard(
                AttendanceStatus.ABSENT, today, scope.zoneIds(), scope.gender(), scope.sewadarId());
        long leaveToday = sewadarRepository.countForDashboard(
                AttendanceStatus.LEAVE, today, scope.zoneIds(), scope.gender(), scope.sewadarId());

        // Male and female for the three cards the dashboard draws twice: one grouped
        // query each, rather than one count per gender per metric.
        DashboardResponse.GenderSplit totalByGender = splitOf(null, today, scope);
        DashboardResponse.GenderSplit presentByGender =
                splitOf(AttendanceStatus.PRESENT, today, scope);
        DashboardResponse.GenderSplit absentByGender =
                splitOf(AttendanceStatus.ABSENT, today, scope);

        // The same measurements one period earlier, so a tile can show real movement.
        // Yesterday for the day tiles; the end of last month for the register total.
        LocalDate yesterday = today.minusDays(1);
        long presentYesterday = sewadarRepository.countForDashboard(
                AttendanceStatus.PRESENT, yesterday, scope.zoneIds(), scope.gender(), scope.sewadarId());
        long absentYesterday = sewadarRepository.countForDashboard(
                AttendanceStatus.ABSENT, yesterday, scope.zoneIds(), scope.gender(), scope.sewadarId());
        long leaveYesterday = sewadarRepository.countForDashboard(
                AttendanceStatus.LEAVE, yesterday, scope.zoneIds(), scope.gender(), scope.sewadarId());
        DashboardResponse.GenderSplit presentByGenderYesterday =
                splitOf(AttendanceStatus.PRESENT, yesterday, scope);
        DashboardResponse.GenderSplit absentByGenderYesterday =
                splitOf(AttendanceStatus.ABSENT, yesterday, scope);

        // The register as the office reads it: local men and women, outstation men
        // and women. Every role sees it, each within their own reach.
        DashboardResponse.LocalitySplit byLocality = localitySplit(scope);

        long totalLastMonth = sewadarRepository.countActiveAsOf(
                monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                scope.zoneIds(), scope.gender(), scope.sewadarId());

        long pendingRequests = requestRepository.countInScope(
                RequestStatus.PENDING, scope.zoneIds(), scope.gender(), scope.sewadarId());
        long pendingLastWeek = requestRepository.countPendingAsOf(
                today.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                scope.zoneIds(), scope.gender(), scope.sewadarId());

        // Account totals are only meaningful - and only permitted - for the roles
        // that administer accounts. Everyone else gets zero here, and the dashboard
        // shows them a different fourth tile.
        boolean seesAccounts = switch (principal.getRole()) {
            case ADMIN, OFFICE_ADMIN -> true;
            default -> false;
        };
        long activeUsers = seesAccounts ? userRepository.countByEnabledTrue() : 0;
        long activeUsersLastMonth = seesAccounts
                ? userRepository.countByEnabledTrueAndCreatedAtLessThanEqual(
                        monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant())
                : 0;

        Map<String, Long> statusBreakdown = attendanceRepository
                .countByStatus(monthStart, monthEnd, scope.zoneIds(), scope.gender(), scope.sewadarId()).stream()
                .collect(Collectors.toMap(r -> r.getStatus().getDisplayName(), r -> r.getCount(),
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Long> sewaTypeBreakdown = attendanceRepository
                .countBySewaType(monthStart, monthEnd, scope.zoneIds(), scope.gender(), scope.sewadarId()).stream()
                .collect(Collectors.toMap(r -> r.getSewaType().getDisplayName(), r -> r.getCount(),
                        (a, b) -> a, LinkedHashMap::new));

        long myMonthPresent = 0;
        double myMonthHours = 0;
        if (scope.sewadarId() != null) {
            var records = attendanceRepository
                    .findBySewadarIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                            scope.sewadarId(), monthStart, monthEnd);
            myMonthPresent = records.stream()
                    .filter(a -> a.getStatus() == AttendanceStatus.PRESENT)
                    .count();
            myMonthHours = records.stream()
                    .filter(a -> a.getHours() != null)
                    .mapToDouble(a -> a.getHours())
                    .sum();
            myMonthHours = Math.round(myMonthHours * 100.0) / 100.0;
        }

        List<DashboardResponse.MonthlyAttendancePoint> monthly = monthlyAttendance(today, scope);

        List<AttendanceResponse> recent = attendanceRepository.search(
                        null, null, null, null, null, null,
                        scope.zoneIds(), scope.gender(), scope.sewadarId(),
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "attendanceDate", "id")))
                .getContent().stream()
                .map(AttendanceResponse::from)
                .toList();

        return new DashboardResponse(
                principal.getFullName(),
                principal.getRole().name(),
                principal.getRole().getDisplayName(),
                scopeLabel(scope),
                today,
                totalSewadars,
                totalByGender,
                presentByGender,
                absentByGender,
                presentByGenderYesterday,
                absentByGenderYesterday,
                byLocality,
                presentToday,
                absentToday,
                leaveToday,
                pendingRequests,
                totalLastMonth,
                presentYesterday,
                absentYesterday,
                leaveYesterday,
                pendingLastWeek,
                activeUsers,
                activeUsersLastMonth,
                myMonthPresent,
                myMonthHours,
                monthly,
                statusBreakdown,
                sewaTypeBreakdown,
                recent);
    }

    /**
     * Present and absent days for each month of this year up to today, in order, with
     * a zero entry for any month that has none - a chart with a gap where February
     * should be is harder to read than one with an empty column.
     */
    private List<DashboardResponse.MonthlyAttendancePoint> monthlyAttendance(LocalDate today,
                                                                             DataScope scope) {
        LocalDate yearStart = today.withDayOfYear(1);
        Map<Integer, Map<AttendanceStatus, Long>> byMonth = new LinkedHashMap<>();
        attendanceRepository
                .countByMonthAndStatus(yearStart, today, scope.zoneIds(), scope.gender(), scope.sewadarId())
                .forEach(row -> byMonth
                        .computeIfAbsent(row.getMonth(), m -> new EnumMap<>(AttendanceStatus.class))
                        .put(row.getStatus(), row.getCount()));

        List<DashboardResponse.MonthlyAttendancePoint> points = new ArrayList<>();
        for (int month = 1; month <= today.getMonthValue(); month++) {
            Map<AttendanceStatus, Long> counts = byMonth.getOrDefault(month, Map.of());
            points.add(new DashboardResponse.MonthlyAttendancePoint(
                    month,
                    Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
                    counts.getOrDefault(AttendanceStatus.ABSENT, 0L)));
        }
        return points;
    }

    /**
     * Folds the grouped rows into male, female and everything else. A gender the
     * query did not return stays at zero, which is the right answer for a zone with
     * no women on its roster - and `other` keeps the people whose record carries no
     * gender at all, so male + female + other always equals the total.
     */
    /** Local and outstation, each split by gender, from one grouped query. */
    private DashboardResponse.LocalitySplit localitySplit(DataScope scope) {
        long[] local = new long[3];
        long[] outstation = new long[3];
        long[] unrecorded = new long[3];
        for (LocalityGenderCount row : sewadarRepository.countByLocalityAndGender(
                scope.zoneIds(), scope.gender(), scope.sewadarId())) {
            long[] side = row.getLocality() == Locality.LOCAL ? local
                    : row.getLocality() == Locality.OUTSTATION ? outstation
                    : unrecorded;
            int slot = row.getGender() == Gender.MALE ? 0 : row.getGender() == Gender.FEMALE ? 1 : 2;
            side[slot] += row.getCount();
        }
        return new DashboardResponse.LocalitySplit(split(local), split(outstation), split(unrecorded));
    }

    private DashboardResponse.GenderSplit split(long[] counts) {
        return new DashboardResponse.GenderSplit(counts[0], counts[1], counts[2]);
    }

    private DashboardResponse.GenderSplit splitOf(AttendanceStatus status,
                                                  LocalDate onDate,
                                                  DataScope scope) {
        long male = 0;
        long female = 0;
        long other = 0;
        for (GenderCount row : sewadarRepository.countForDashboardByGender(
                status, onDate, scope.zoneIds(), scope.gender(), scope.sewadarId())) {
            if (row.getGender() == Gender.MALE) {
                male += row.getCount();
            } else if (row.getGender() == Gender.FEMALE) {
                female += row.getCount();
            } else {
                other += row.getCount();
            }
        }
        return new DashboardResponse.GenderSplit(male, female, other);
    }

    private String scopeLabel(DataScope scope) {
        if (scope.isGlobal()) {
            return "All zones";
        }
        if (scope.sewadarId() != null) {
            return "My records only";
        }
        List<String> names = zoneService.listAccessible(true).stream()
                .map(z -> z.name())
                .toList();
        return names.isEmpty() ? "No zone assigned" : String.join(", ", names);
    }
}

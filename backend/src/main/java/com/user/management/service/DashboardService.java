package com.user.management.service;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.RequestStatus;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.DashboardResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
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
import java.time.YearMonth;
import java.util.LinkedHashMap;
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

        long totalSewadars;
        if (scope.sewadarId() != null) {
            totalSewadars = 1;
        } else if (scope.zoneIds() == null) {
            totalSewadars = sewadarRepository.countByActiveTrue();
        } else {
            totalSewadars = sewadarRepository.countByZoneIdInAndActiveTrue(scope.zoneIds());
        }

        long presentToday = countToday(today, AttendanceStatus.PRESENT, scope);
        long absentToday = countToday(today, AttendanceStatus.ABSENT, scope);

        long pendingRequests = requestRepository.countInScope(
                RequestStatus.PENDING, scope.zoneIds(), scope.sewadarId());

        Map<String, Long> statusBreakdown = attendanceRepository
                .countByStatus(monthStart, monthEnd, scope.zoneIds(), scope.sewadarId()).stream()
                .collect(Collectors.toMap(r -> r.getStatus().getDisplayName(), r -> r.getCount(),
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Long> sewaTypeBreakdown = attendanceRepository
                .countBySewaType(monthStart, monthEnd, scope.zoneIds(), scope.sewadarId()).stream()
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

        List<AttendanceResponse> recent = attendanceRepository.search(
                        null, null, null, null, null, null,
                        scope.zoneIds(), scope.sewadarId(),
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
                presentToday,
                absentToday,
                pendingRequests,
                myMonthPresent,
                myMonthHours,
                statusBreakdown,
                sewaTypeBreakdown,
                recent);
    }

    private long countToday(LocalDate today, AttendanceStatus status, DataScope scope) {
        if (scope.sewadarId() != null) {
            return attendanceRepository
                    .findBySewadarIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                            scope.sewadarId(), today, today).stream()
                    .filter(a -> a.getStatus() == status)
                    .count();
        }
        if (scope.zoneIds() == null) {
            return attendanceRepository.countByAttendanceDateAndStatus(today, status);
        }
        return attendanceRepository.countByAttendanceDateAndStatusAndZoneIdIn(today, status, scope.zoneIds());
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

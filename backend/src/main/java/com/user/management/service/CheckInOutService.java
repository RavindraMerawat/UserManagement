package com.user.management.service;

import com.user.management.entity.Attendance;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.BulkCheckInOutRequest;
import com.user.management.model.CheckInOutRequest;
import com.user.management.model.CheckInOutResult;
import com.user.management.model.SewadarLookupResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.security.AadharMask;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Check in and check out, for one sewadar from the Mark Attendance screen or for a
 * whole zone at once.
 *
 * <p>Both sit on the existing attendance row keyed by (sewadar, date, sewa type):
 * check in sets the in time and marks the day present, check out sets the out time
 * and lets the entity derive the hours. Checking the same person in twice is refused
 * rather than silently overwriting the original arrival time.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInOutService {

    private static final SewaType DEFAULT_SEWA = SewaType.DAILY_SEWA;
    private static final int MAX_LOOKUP_HITS = 25;

    private final SewadarRepository sewadarRepository;
    private final AttendanceRepository attendanceRepository;
    private final CurrentUserService currentUser;

    // ------------------------------------------------------------------ lookup

    /** Finds sewadars by badge number, name, mobile or Aadhaar, within scope. */
    @Transactional(readOnly = true)
    public List<SewadarLookupResponse> lookup(String query, SewaType sewaType, LocalDate onDate) {
        if (!StringUtils.hasText(query) || query.trim().length() < 2) {
            throw new BadRequestException("Enter at least 2 characters to search");
        }
        DataScope scope = currentUser.scope();
        String trimmed = query.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");

        List<Sewadar> hits = sewadarRepository.lookup(
                trimmed.toLowerCase(),
                trimmed.toLowerCase(),
                // An empty digit string would make the mobile LIKE match everything.
                digits.isEmpty() ? " " : digits,
                scope.zoneIds(), scope.gender(),
                scope.sewadarId(),
                PageRequest.of(0, MAX_LOOKUP_HITS));

        LocalDate date = onDate == null ? LocalDate.now() : onDate;
        SewaType type = sewaType == null ? DEFAULT_SEWA : sewaType;

        return hits.stream().map(s -> toLookup(s, date, type)).toList();
    }

    private SewadarLookupResponse toLookup(Sewadar s, LocalDate date, SewaType type) {
        Optional<Attendance> today = attendanceRepository
                .findBySewadarIdAndAttendanceDateAndSewaType(s.getId(), date, type);

        Attendance a = today.orElse(null);
        String aadhar = s.getAadharNumber();
        boolean maskAadhar = aadhar != null && !aadhar.isBlank()
                && !currentUser.canViewFullAadhar(s.getId());
        return new SewadarLookupResponse(
                s.getId(),
                s.getBadgeNumber(),
                s.getName(),
                s.getFatherOrHusbandName(),
                s.getMobile(),
                maskAadhar ? AadharMask.mask(aadhar) : aadhar,
                maskAadhar,
                s.getDepartment(),
                s.getZone() == null ? null : s.getZone().getName(),
                s.getArea(),
                s.getCenterPoint(),
                true,
                s.getPhotoUpdatedAt() != null,
                s.getPhotoUpdatedAt(),
                a == null ? null : a.getId(),
                type.name(),
                type.getDisplayName(),
                a == null ? null : a.getStatus().name(),
                a == null ? null : a.getStatus().getDisplayName(),
                a == null ? null : a.getInTime(),
                a == null ? null : a.getOutTime(),
                a == null ? null : a.getHours(),
                a != null && a.getInTime() != null,
                a != null && a.getOutTime() != null);
    }

    /** Today's status for one sewadar, used to refresh the card after an action. */
    @Transactional(readOnly = true)
    public SewadarLookupResponse status(Long sewadarId, SewaType sewaType, LocalDate onDate) {
        Sewadar sewadar = loadInScope(sewadarId);
        return toLookup(sewadar,
                onDate == null ? LocalDate.now() : onDate,
                sewaType == null ? DEFAULT_SEWA : sewaType);
    }

    // ------------------------------------------------------------- single check

    @Transactional
    public AttendanceResponse checkIn(CheckInOutRequest request) {
        requirePermission();
        Sewadar sewadar = loadForMarking(request.sewadarId());
        LocalDate date = dateOf(request.attendanceDate());
        SewaType type = request.sewaType() == null ? DEFAULT_SEWA : request.sewaType();
        LocalTime at = request.time() == null ? LocalTime.now().withNano(0) : request.time();

        Attendance attendance = attendanceRepository
                .findBySewadarIdAndAttendanceDateAndSewaType(sewadar.getId(), date, type)
                .orElseGet(() -> Attendance.builder()
                        .sewadar(sewadar)
                        .zone(sewadar.getZone())
                        .attendanceDate(date)
                        .sewaType(type)
                        .build());

        if (attendance.getInTime() != null) {
            throw new BadRequestException(sewadar.getName() + " already checked in at "
                    + attendance.getInTime() + ". Check out instead.");
        }

        attendance.setInTime(at);
        attendance.setStatus(AttendanceStatus.PRESENT);
        if (StringUtils.hasText(request.remarks())) {
            attendance.setRemarks(request.remarks());
        }
        attendance.setMarkedBy(currentUser.username());

        Attendance saved = attendanceRepository.save(attendance);
        log.info("{} checked in {} ({}) at {}", currentUser.username(),
                sewadar.getBadgeNumber(), type, at);
        return AttendanceResponse.from(saved);
    }

    @Transactional
    public AttendanceResponse checkOut(CheckInOutRequest request) {
        requirePermission();
        Sewadar sewadar = loadForMarking(request.sewadarId());
        LocalDate date = dateOf(request.attendanceDate());
        SewaType type = request.sewaType() == null ? DEFAULT_SEWA : request.sewaType();
        LocalTime at = request.time() == null ? LocalTime.now().withNano(0) : request.time();

        Attendance attendance = attendanceRepository
                .findBySewadarIdAndAttendanceDateAndSewaType(sewadar.getId(), date, type)
                .orElseThrow(() -> new BadRequestException(
                        sewadar.getName() + " has not checked in for this sewa yet"));

        if (attendance.getInTime() == null) {
            throw new BadRequestException(sewadar.getName() + " has not checked in yet");
        }
        if (attendance.getOutTime() != null) {
            throw new BadRequestException(sewadar.getName() + " already checked out at "
                    + attendance.getOutTime());
        }
        if (!at.isAfter(attendance.getInTime())) {
            throw new BadRequestException("Check out time must be after the check in time of "
                    + attendance.getInTime());
        }

        attendance.setOutTime(at);
        if (StringUtils.hasText(request.remarks())) {
            attendance.setRemarks(request.remarks());
        }
        attendance.setMarkedBy(currentUser.username());
        attendance.recalculateHours();

        Attendance saved = attendanceRepository.save(attendance);
        log.info("{} checked out {} ({}) at {} - {} hrs", currentUser.username(),
                sewadar.getBadgeNumber(), type, at, saved.getHours());
        return AttendanceResponse.from(saved);
    }

    // --------------------------------------------------------------- bulk check

    @Transactional
    public CheckInOutResult bulkCheckIn(BulkCheckInOutRequest request) {
        return bulk(request, true);
    }

    @Transactional
    public CheckInOutResult bulkCheckOut(BulkCheckInOutRequest request) {
        return bulk(request, false);
    }

    /**
     * A row that cannot be marked (already checked in, not checked in yet,
     * out of scope) is collected as a skipped row instead of aborting the batch.
     */
    private CheckInOutResult bulk(BulkCheckInOutRequest request, boolean checkingIn) {
        requirePermission();
        LocalDate date = dateOf(request.attendanceDate());
        SewaType type = request.sewaType() == null ? DEFAULT_SEWA : request.sewaType();
        LocalTime at = request.time() == null ? LocalTime.now().withNano(0) : request.time();

        List<AttendanceResponse> marked = new ArrayList<>();
        List<CheckInOutResult.Skipped> skipped = new ArrayList<>();

        for (Long sewadarId : request.sewadarIds().stream().distinct().toList()) {
            String name = null;
            try {
                name = sewadarRepository.findById(sewadarId).map(Sewadar::getName).orElse(null);
                CheckInOutRequest one = new CheckInOutRequest(
                        sewadarId, type, date, at, request.remarks());
                marked.add(checkingIn ? checkIn(one) : checkOut(one));
            } catch (BadRequestException | ForbiddenException | NotFoundException e) {
                skipped.add(new CheckInOutResult.Skipped(sewadarId, name, e.getMessage()));
            }
        }

        log.info("{} bulk {} on {}: {} marked, {} skipped", currentUser.username(),
                checkingIn ? "check in" : "check out", date, marked.size(), skipped.size());

        return new CheckInOutResult(
                request.sewadarIds().size(), marked.size(), skipped.size(), marked, skipped);
    }

    // ------------------------------------------------------------------ helpers

    private Sewadar loadInScope(Long sewadarId) {
        Sewadar sewadar = sewadarRepository.findById(sewadarId)
                .orElseThrow(() -> NotFoundException.of("Sewadar", sewadarId));
        currentUser.requireSewadarAccess(sewadar.getId(), sewadar.getZone().getId());
        return sewadar;
    }

    private Sewadar loadForMarking(Long sewadarId) {
        Sewadar sewadar = sewadarRepository.findById(sewadarId)
                .orElseThrow(() -> NotFoundException.of("Sewadar", sewadarId));
        currentUser.requireZoneAccess(sewadar.getZone().getId());
        return sewadar;
    }

    private LocalDate dateOf(LocalDate requested) {
        LocalDate date = requested == null ? LocalDate.now() : requested;
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Attendance cannot be marked for a future date");
        }
        return date;
    }

    private void requirePermission() {
        if (!currentUser.canMarkAttendance()) {
            throw new ForbiddenException("Your role cannot mark or update attendance");
        }
    }
}

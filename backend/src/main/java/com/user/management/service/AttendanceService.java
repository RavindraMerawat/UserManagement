package com.user.management.service;

import com.user.management.entity.Attendance;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.AttendanceRequest;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.BulkAttendanceRequest;
import com.user.management.model.PageResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SewadarRepository sewadarRepository;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> search(Long sewadarId,
                                                   Long zoneId,
                                                   SewaType sewaType,
                                                   AttendanceStatus status,
                                                   LocalDate from,
                                                   LocalDate to,
                                                   Pageable pageable) {
        DataScope scope = currentUser.scope();
        if (zoneId != null) {
            currentUser.requireZoneAccess(zoneId);
        }
        if (sewadarId != null && !scope.allowsSewadar(sewadarId)) {
            throw new ForbiddenException("You can only view your own attendance");
        }
        return PageResponse.of(
                attendanceRepository.search(sewadarId, zoneId, sewaType, status, from, to,
                        scope.zoneIds(), scope.sewadarId(), pageable),
                AttendanceResponse::from);
    }

    /** Attendance for the signed-in sewadar over a date range. */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> myAttendance(LocalDate from, LocalDate to) {
        Long sewadarId = currentUser.principal().getSewadarId();
        if (sewadarId == null) {
            throw new BadRequestException("This account is not linked to a sewadar record");
        }
        return attendanceRepository
                .findBySewadarIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(sewadarId, from, to)
                .stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceResponse get(Long id) {
        return AttendanceResponse.from(getInScope(id));
    }

    /**
     * Marks or overwrites a single attendance entry. The unique key is
     * (sewadar, date, sewa type), so calling this twice updates rather than duplicates.
     */
    @Transactional
    public AttendanceResponse mark(AttendanceRequest request) {
        requireMarkPermission();
        Sewadar sewadar = loadSewadarForMarking(request.sewadarId());
        validateDate(request.attendanceDate());

        Attendance attendance = attendanceRepository
                .findBySewadarIdAndAttendanceDateAndSewaType(
                        sewadar.getId(), request.attendanceDate(), request.sewaType())
                .orElseGet(() -> Attendance.builder()
                        .sewadar(sewadar)
                        .zone(sewadar.getZone())
                        .attendanceDate(request.attendanceDate())
                        .sewaType(request.sewaType())
                        .build());

        applyFields(attendance, request.status(), request.inTime(), request.outTime(),
                request.hours(), request.remarks());
        return AttendanceResponse.from(attendanceRepository.save(attendance));
    }

    /** Marks a full sewa sheet for one date and sewa type. */
    @Transactional
    public List<AttendanceResponse> markBulk(BulkAttendanceRequest request) {
        requireMarkPermission();
        validateDate(request.attendanceDate());

        List<AttendanceResponse> saved = new ArrayList<>();
        for (BulkAttendanceRequest.Entry entry : request.entries()) {
            Sewadar sewadar = loadSewadarForMarking(entry.sewadarId());

            Attendance attendance = attendanceRepository
                    .findBySewadarIdAndAttendanceDateAndSewaType(
                            sewadar.getId(), request.attendanceDate(), request.sewaType())
                    .orElseGet(() -> Attendance.builder()
                            .sewadar(sewadar)
                            .zone(sewadar.getZone())
                            .attendanceDate(request.attendanceDate())
                            .sewaType(request.sewaType())
                            .build());

            LocalTime in = entry.inTime() != null ? entry.inTime() : request.inTime();
            LocalTime out = entry.outTime() != null ? entry.outTime() : request.outTime();
            applyFields(attendance, entry.status(), in, out, null, entry.remarks());
            saved.add(AttendanceResponse.from(attendanceRepository.save(attendance)));
        }
        log.info("{} marked {} attendance entries for {} ({})",
                currentUser.username(), saved.size(), request.attendanceDate(), request.sewaType());
        return saved;
    }

    @Transactional
    public AttendanceResponse update(Long id, AttendanceRequest request) {
        requireMarkPermission();
        Attendance attendance = getInScope(id);

        if (request.sewadarId() != null && !request.sewadarId().equals(attendance.getSewadar().getId())) {
            throw new BadRequestException(
                    "The sewadar of an existing entry cannot be changed. Delete it and mark a new one.");
        }
        if (request.attendanceDate() != null) {
            validateDate(request.attendanceDate());
            attendance.setAttendanceDate(request.attendanceDate());
        }
        if (request.sewaType() != null) {
            attendance.setSewaType(request.sewaType());
        }
        applyFields(attendance, request.status(), request.inTime(), request.outTime(),
                request.hours(), request.remarks());
        return AttendanceResponse.from(attendanceRepository.save(attendance));
    }

    @Transactional
    public void delete(Long id) {
        if (!currentUser.canManageSewadars()) {
            throw new ForbiddenException("Only Admin and Office Admin can delete an attendance entry");
        }
        attendanceRepository.delete(getInScope(id));
    }

    private void applyFields(Attendance attendance,
                             AttendanceStatus status,
                             LocalTime inTime,
                             LocalTime outTime,
                             Double hours,
                             String remarks) {
        if (status != null) {
            attendance.setStatus(status);
        }
        attendance.setInTime(inTime);
        attendance.setOutTime(outTime);
        if (inTime != null && outTime != null && !outTime.isAfter(inTime)) {
            throw new BadRequestException("Out time must be after in time");
        }
        // An explicit hours value wins; otherwise @PrePersist derives it from in/out time.
        if (hours != null) {
            attendance.setHours(hours);
        } else if (inTime == null || outTime == null) {
            attendance.setHours(null);
        }
        attendance.setRemarks(remarks);
        attendance.setMarkedBy(currentUser.username());
    }

    private Attendance getInScope(Long id) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Attendance", id));
        currentUser.requireSewadarAccess(attendance.getSewadar().getId(), attendance.getZone().getId());
        return attendance;
    }

    /** Loads the sewadar and confirms the caller may mark attendance for that zone. */
    private Sewadar loadSewadarForMarking(Long sewadarId) {
        Sewadar sewadar = sewadarRepository.findById(sewadarId)
                .orElseThrow(() -> NotFoundException.of("Sewadar", sewadarId));
        if (!sewadar.isActive()) {
            throw new BadRequestException("Sewadar " + sewadar.getName() + " is inactive");
        }
        currentUser.requireZoneAccess(sewadar.getZone().getId());
        return sewadar;
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BadRequestException("Attendance cannot be marked for a future date");
        }
    }

    private void requireMarkPermission() {
        if (!currentUser.canMarkAttendance()) {
            throw new ForbiddenException("Your role cannot mark or update attendance");
        }
    }
}

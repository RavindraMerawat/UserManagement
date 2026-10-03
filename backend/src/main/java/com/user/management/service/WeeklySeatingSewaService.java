package com.user.management.service;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.BadgeAction;
import com.user.management.entity.Gender;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewaType;
import com.user.management.entity.WeekDay;
import com.user.management.entity.WeeklySeatingSewa;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.AttendanceRequest;
import com.user.management.model.PageResponse;
import com.user.management.model.SeatingDaySummary;
import com.user.management.model.SeatingPersonResponse;
import com.user.management.model.WeeklySeatingSewaRequest;
import com.user.management.model.WeeklySeatingSewaResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.WeeklySeatingSewaRepository;
import com.user.management.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Weekly seating sewa: who sat on a given Sunday or Thursday, and the token they held.
 *
 * <p>Recording one also marks that day's attendance. The office does both from the
 * same desk at the same moment, and two screens for one act is how a person ends up
 * seated on the sheet and absent in the reports.</p>
 *
 * <p>This is badge work, so it follows the badge rule: Admin, Office Incharge and
 * Office Sewadar record it, everyone else on the badge screen may read.</p>
 */
@Service
@RequiredArgsConstructor
public class WeeklySeatingSewaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    /** Seating has its own sewa type, so the register says what the day was. */
    private static final SewaType ATTENDANCE_TYPE = SewaType.WEEKLY_SEWA;

    private final WeeklySeatingSewaRepository repository;
    private final SewadarRepository sewadarRepository;
    private final AttendanceService attendanceService;
    private final CurrentUserService currentUser;

    /** One sewadar's seating days, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<WeeklySeatingSewaResponse> forSewadar(Long sewadarId, Pageable pageable) {
        Sewadar sewadar = readableSewadar(sewadarId);
        return PageResponse.of(
                repository.findBySewadarIdOrderBySewaDateDesc(sewadar.getId(), pageable),
                WeeklySeatingSewaResponse::from);
    }

    /**
     * Hands the badge out, or takes it back - and writes everything that follows.
     *
     * <p>One call does the lot: the seating row for the day, the day's attendance,
     * and the sewadar's own badge flag. There is no separate "submit" step because
     * there is no separate moment - the person is at the desk, the badge changes
     * hands, and that is the event. Two buttons that each saved half of it left
     * records where the token was written down and the attendance was not.</p>
     *
     * <p>Issuing and receiving on the same day update the one row rather than making
     * two, which is why this upserts: a badge that goes out in the morning and comes
     * back at night is one seating, not two.</p>
     */
    @Transactional
    public WeeklySeatingSewaResponse record(WeeklySeatingSewaRequest request) {
        requireBadgePermission();
        Sewadar sewadar = readableSewadar(request.sewadarId());
        requireDayMatchesDate(request);

        String token = request.tokenNo().trim();
        WeeklySeatingSewa row = repository
                .findBySewadarIdAndSewaDate(sewadar.getId(), request.sewaDate())
                .orElseGet(() -> WeeklySeatingSewa.builder()
                        .sewadar(sewadar)
                        .sewaDate(request.sewaDate())
                        .build());

        // A token already with somebody else that day is a clash whether the row is
        // new or not; the same token on this person's own row is simply itself.
        repository.findBySewaDateAndTokenNoIgnoreCase(request.sewaDate(), token)
                .filter(held -> !held.getSewadar().getId().equals(sewadar.getId()))
                .ifPresent(held -> {
                    // The token as recorded, not as typed: it is the number written on
                    // the badge in somebody's hand, and "t-014" sends the person
                    // looking for a badge that does not exist.
                    throw new BadRequestException("Badge No " + held.getTokenNo()
                            + " is already with " + held.getSewadar().getName() + " on "
                            + request.sewaDate().format(DAY) + ".");
                });

        row.setWeekDay(request.weekDay());
        row.setTokenNo(token);
        // Seconds kept, not trimmed: the out time has to be after the in time, and
        // a badge handed over and taken back inside the same minute is unusual but
        // not impossible.
        LocalTime now = LocalTime.now().withNano(0);
        if (request.action() == BadgeAction.ISSUE) {
            row.setBadgeIssued(true);
            row.setIssuedAt(now);
            sewadar.setBadgeIssued(true);
        } else {
            if (!row.isBadgeIssued()) {
                throw new BadRequestException("Badge No " + token + " has not been issued to "
                        + sewadar.getName() + " on " + request.sewaDate().format(DAY)
                        + ". Issue it before taking it back.");
            }
            row.setBadgeReceived(true);
            row.setReceivedAt(now);
            sewadar.setBadgeReceived(true);
        }
        sewadarRepository.save(sewadar);
        WeeklySeatingSewa saved = repository.save(row);

        /*
          * The badge going out is the check in and coming back is the check out, so
          * the seating desk marks the whole day without anybody visiting Mark
          * Attendance. Both times are passed every time because the attendance row
          * is rewritten on each call - sending only the new one would wipe the other.
          */
        LocalTime in = row.getIssuedAt();
        LocalTime out = row.getReceivedAt();
        if (in != null && out != null && !out.isAfter(in)) {
            // Handed back within the same second. A second apart is not a real
            // measurement either way, and it keeps the row valid.
            out = in.plusSeconds(1);
        }

        attendanceService.mark(new AttendanceRequest(
                sewadar.getId(),
                request.sewaDate(),
                ATTENDANCE_TYPE,
                AttendanceStatus.PRESENT,
                in, out, null,
                "Weekly seating sewa · Badge No " + token));

        return WeeklySeatingSewaResponse.from(saved);
    }

    /** The day's four numbers, within the caller's zones. */
    @Transactional(readOnly = true)
    public SeatingDaySummary summarise(LocalDate date) {
        Object[] row = repository.summariseDay(date, currentUser.scope().zoneIds(), currentUser.scope().gender()).get(0);
        return new SeatingDaySummary(count(row[0]), count(row[1]), count(row[2]), count(row[3]));
    }

    /** The people behind one of those numbers. */
    @Transactional(readOnly = true)
    public PageResponse<SeatingPersonResponse> dayMovements(LocalDate date, BadgeAction action,
                                                            Gender gender, Pageable pageable) {
        return PageResponse.of(
                repository.findDayMovements(date, action == BadgeAction.ISSUE, gender,
                        currentUser.scope().zoneIds(), currentUser.scope().gender(), pageable),
                SeatingPersonResponse::from);
    }

    /** A sum over no rows is null, which is a count of nothing. */
    private long count(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** Removes one seating record. The attendance it marked is left alone. */
    @Transactional
    public void delete(Long id) {
        requireBadgePermission();
        WeeklySeatingSewa row = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Weekly seating sewa", id));
        readableSewadar(row.getSewadar().getId());
        repository.delete(row);
    }

    /**
     * The chosen day must be the day the date actually falls on.
     *
     * <p>Without this, a date typed a day out is recorded as a Sunday that was a
     * Monday, and nothing downstream can tell. The message names both so the person
     * can see which of the two they got wrong.</p>
     */
    private void requireDayMatchesDate(WeeklySeatingSewaRequest request) {
        WeekDay actual = request.sewaDate().getDayOfWeek() == WeekDay.SUNDAY.getDayOfWeek()
                ? WeekDay.SUNDAY
                : request.sewaDate().getDayOfWeek() == WeekDay.THURSDAY.getDayOfWeek()
                        ? WeekDay.THURSDAY
                        : null;

        if (actual == null) {
            throw new BadRequestException(request.sewaDate().format(DAY) + " is a "
                    + friendly(request.sewaDate().getDayOfWeek().name())
                    + ". Weekly seating sewa is on a Sunday or a Thursday.");
        }
        if (actual != request.weekDay()) {
            throw new BadRequestException(request.sewaDate().format(DAY) + " is a "
                    + actual.getDisplayName() + ", not a " + request.weekDay().getDisplayName()
                    + ".");
        }
    }

    private String friendly(String dayName) {
        return dayName.charAt(0) + dayName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /** The sewadar, if this person is allowed to see them at all. */
    private Sewadar readableSewadar(Long sewadarId) {
        Sewadar sewadar = sewadarRepository.findById(sewadarId)
                .orElseThrow(() -> NotFoundException.of("Sewadar", sewadarId));
        currentUser.requireSewadarAccess(sewadar.getId(),
                sewadar.getZone() == null ? null : sewadar.getZone().getId());
        return sewadar;
    }

    private void requireBadgePermission() {
        if (!currentUser.canManageBadges()) {
            throw new ForbiddenException(
                    "Your role can view seating sewa but cannot record it");
        }
    }
}

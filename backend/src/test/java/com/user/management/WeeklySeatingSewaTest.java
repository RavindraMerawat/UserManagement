package com.user.management;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.BadgeAction;
import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.entity.WeeklySeatingSewa;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.User;
import com.user.management.entity.WeekDay;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.model.SeatingDaySummary;
import com.user.management.model.SeatingPersonResponse;
import com.user.management.model.WeeklySeatingSewaRequest;
import com.user.management.model.WeeklySeatingSewaResponse;
import com.user.management.model.AttendanceRequest;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.WeeklySeatingSewaRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.WeeklySeatingSewaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Weekly seating sewa: the day must be the day, a sewadar is seated once, a token is
 * held by one person, and recording it marks the attendance in the same breath.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeeklySeatingSewaTest {

    @Autowired WeeklySeatingSewaService service;
    @Autowired com.user.management.service.SewadarService sewadarService;
    @Autowired WeeklySeatingSewaRepository repository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired com.user.management.service.AttendanceService attendanceService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired SewadarRoleRepository sewadarRoleRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    /** The most recent Sunday and Thursday, so the dates are real and not in the future. */
    private static final LocalDate LAST_SUNDAY =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    private static final LocalDate LAST_THURSDAY =
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY));
    private static final LocalDate A_MONDAY =
            LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.MONDAY));

    private Zone north;
    private Sewadar first;
    private Sewadar second;

    @BeforeEach
    void seed() {
        north = zoneRepository.save(Zone.builder().code("WN").name("North").active(true).build());
        first = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("W-100").name("First Sewadar").zone(north).build());
        second = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("W-200").name("Second Sewadar").zone(north).build());
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
    }

    // ------------------------------------------------ recording, and what follows

    @Test
    @DisplayName("recording a seating also marks that day present")
    void recordingMarksAttendance() {
        WeeklySeatingSewaResponse saved = service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));

        assertThat(saved.tokenNo()).isEqualTo("T-014");
        assertThat(saved.weekDayLabel()).isEqualTo("Sunday");

        assertThat(attendanceRepository.findBySewadarIdAndAttendanceDateAndSewaType(
                first.getId(), LAST_SUNDAY, SewaType.WEEKLY_SEWA))
                .get()
                .satisfies(row -> {
                    assertThat(row.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
                    assertThat(row.getRemarks()).contains("T-014");
                    // The badge going out is the check in; nothing has come back yet.
                    assertThat(row.getInTime()).isNotNull();
                    assertThat(row.getOutTime()).isNull();
                });
    }

    @Test
    @DisplayName("issuing then receiving the same day is one seating, not two")
    void issueThenReceiveIsOneRow() {
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));
        WeeklySeatingSewaResponse back = service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.RECEIVE));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(back.badgeIssued()).isTrue();
        assertThat(back.badgeReceived()).isTrue();
    }

    @Test
    @DisplayName("a badge cannot come back before it went out")
    void receiveNeedsAnIssue() {
        assertThatThrownBy(() -> service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.RECEIVE)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("has not been issued");
    }

    @Test
    @DisplayName("the day's counts split by gender, and the people behind them")
    void theDaysCounts() {
        first.setGender(Gender.MALE);
        second.setGender(Gender.FEMALE);
        sewadarRepository.save(first);
        sewadarRepository.save(second);

        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));
        service.record(new WeeklySeatingSewaRequest(
                second.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-015", BadgeAction.ISSUE));
        service.record(new WeeklySeatingSewaRequest(
                second.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-015", BadgeAction.RECEIVE));

        SeatingDaySummary summary = service.summarise(LAST_SUNDAY);
        assertThat(summary.issuedMale()).isEqualTo(1);
        assertThat(summary.issuedFemale()).isEqualTo(1);
        assertThat(summary.receivedMale()).isZero();
        assertThat(summary.receivedFemale()).isEqualTo(1);

        assertThat(service.dayMovements(LAST_SUNDAY, BadgeAction.ISSUE, null,
                PageRequest.of(0, 20)).content())
                .extracting(SeatingPersonResponse::badgeNumber)
                .containsExactlyInAnyOrder("W-100", "W-200");

        assertThat(service.dayMovements(LAST_SUNDAY, BadgeAction.RECEIVE, Gender.FEMALE,
                PageRequest.of(0, 20)).content())
                .extracting(SeatingPersonResponse::name)
                .containsExactly("Second Sewadar");
    }

    @Test
    @DisplayName("a token already with somebody on that day is refused, and names them")
    void aTokenIsHeldByOnePerson() {
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));

        assertThatThrownBy(() -> service.record(new WeeklySeatingSewaRequest(
                second.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "t-014", BadgeAction.ISSUE)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("T-014")
                .hasMessageContaining("First Sewadar");
    }

    @Test
    @DisplayName("the same token on a different day is fine")
    void tokensAreReusedNextWeek() {
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));
        service.record(new WeeklySeatingSewaRequest(
                second.getId(), LAST_THURSDAY, WeekDay.THURSDAY, "T-014", BadgeAction.ISSUE));

        assertThat(repository.count()).isEqualTo(2);
    }

    // ------------------------------------------------------- the day and the date

    @Test
    @DisplayName("a date that is not the chosen day is refused, and says which it is")
    void theDayMustMatchTheDate() {
        assertThatThrownBy(() -> service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.THURSDAY, "T-014", BadgeAction.ISSUE)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("is a Sunday, not a Thursday");
    }

    @Test
    @DisplayName("a date that is neither Sunday nor Thursday is refused outright")
    void onlyTwoDaysAreSeatingDays() {
        assertThatThrownBy(() -> service.record(new WeeklySeatingSewaRequest(
                first.getId(), A_MONDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Monday");
    }

    @Test
    @DisplayName("issuing checks the day in and receiving checks it out")
    void theDayIsCheckedInAndOut() {
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.RECEIVE));

        assertThat(attendanceRepository.findBySewadarIdAndAttendanceDateAndSewaType(
                first.getId(), LAST_SUNDAY, SewaType.WEEKLY_SEWA))
                .get()
                .satisfies(row -> {
                    assertThat(row.getInTime()).isNotNull();
                    assertThat(row.getOutTime()).isNotNull();
                    // Whatever the clock did, the day reads the right way round.
                    assertThat(row.getOutTime()).isAfter(row.getInTime());
                });
    }

    // ------------------------------------------------------------ who may record

    @Test
    @DisplayName("the zone trio may read seating but not record it")
    void theZoneTrioIsReadOnly() {
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));

        for (String designation : new String[] { "Coordinator", "Zone Incharge", "Supervisor" }) {
            signInAs(Role.COORDINATOR, designation, Set.of(north.getId()));

            assertThat(service.forSewadar(first.getId(), PageRequest.of(0, 20)).content())
                    .as("%s can read", designation)
                    .hasSize(1);

            assertThatThrownBy(() -> service.record(new WeeklySeatingSewaRequest(
                    second.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-015", BadgeAction.ISSUE)))
                    .as("%s cannot record", designation)
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("cannot record");
        }
    }

    @Test
    @DisplayName("removing a seating leaves the attendance it marked alone")
    void removingKeepsTheAttendance() {
        WeeklySeatingSewaResponse saved = service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-014", BadgeAction.ISSUE));

        service.delete(saved.id());

        assertThat(repository.count()).isZero();
        assertThat(attendanceRepository.findBySewadarIdAndAttendanceDateAndSewaType(
                first.getId(), LAST_SUNDAY, SewaType.WEEKLY_SEWA)).isPresent();
    }

    @Test
    @DisplayName("the grid carries the day's check in and check out")
    void theGridCarriesTheTimes() {
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-030", BadgeAction.ISSUE));

        SeatingPersonResponse afterIssue = onlyRow(BadgeAction.ISSUE);
        // The badge going out is the check in; it has not come back yet.
        assertThat(afterIssue.checkInTime()).isNotNull();
        assertThat(afterIssue.checkOutTime()).isNull();

        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-030", BadgeAction.RECEIVE));

        SeatingPersonResponse afterReceive = onlyRow(BadgeAction.ISSUE);
        assertThat(afterReceive.checkInTime()).isEqualTo(afterIssue.checkInTime());
        assertThat(afterReceive.checkOutTime()).isNotNull();
    }

    @Test
    @DisplayName("the times are this desk's own, not whatever else the day holds")
    void otherAttendanceDoesNotReachThisGrid() {
        /*
         * The office asked for *the weekly seating's* check in and check out in this
         * list, not the person's attendance at large. Somebody who sat on Sunday and
         * was also marked present for another sewa the same day has two pairs of
         * times, and only the seating desk's pair belongs on the seating desk's
         * screen.
         */
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-031", BadgeAction.ISSUE));
        LocalTime seatingCheckIn = onlyRow(BadgeAction.ISSUE).checkInTime();

        attendanceService.mark(new AttendanceRequest(
                first.getId(), LAST_SUNDAY, SewaType.DAILY_SEWA, AttendanceStatus.PRESENT,
                LocalTime.of(5, 0), LocalTime.of(6, 0), null, "Some other sewa"));

        SeatingPersonResponse row = onlyRow(BadgeAction.ISSUE);
        assertThat(row.checkInTime()).isEqualTo(seatingCheckIn);
        assertThat(row.checkInTime()).isNotEqualTo(LocalTime.of(5, 0));
        assertThat(row.checkOutTime()).isNull();
    }

    @Test
    @DisplayName("a weekly seating leaves the annual satsang badge alone")
    void theWeeklyBadgeIsNotTheAnnualBadge() {
        /*
         * Two badges, two tabs. `sewadars.badgeIssued` is the annual satsang badge
         * and is what the Annual Satsang Sewa cards count; the weekly badge is the
         * one on the seating row, dated. Writing the first from the second made a
         * Sunday seating move 23 people from Pending to Issued on an event that had
         * not happened, and the office reported the number.
         */
        assertThat(first.isBadgeIssued()).isFalse();

        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-040", BadgeAction.ISSUE));
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-040", BadgeAction.RECEIVE));

        Sewadar after = sewadarRepository.findById(first.getId()).orElseThrow();
        assertThat(after.isBadgeIssued()).isFalse();
        assertThat(after.isBadgeReceived()).isFalse();

        // The weekly badge did happen, and is recorded where it belongs.
        WeeklySeatingSewa seating = repository
                .findBySewadarIdAndSewaDate(first.getId(), LAST_SUNDAY).orElseThrow();
        assertThat(seating.isBadgeIssued()).isTrue();
        assertThat(seating.isBadgeReceived()).isTrue();
    }

    @Test
    @DisplayName("the annual badge can still be issued to somebody who sat on Sunday")
    void sittingOnSundayDoesNotLockTheAnnualBadge() {
        // The other half of the same bug: issueBadge refuses a badge that is already
        // issued, so a seated sewadar could not be given their annual one at all.
        service.record(new WeeklySeatingSewaRequest(
                first.getId(), LAST_SUNDAY, WeekDay.SUNDAY, "T-041", BadgeAction.ISSUE));

        sewadarService.issueBadge(first.getId());

        assertThat(sewadarRepository.findById(first.getId()).orElseThrow().isBadgeIssued()).isTrue();
    }

    /** The one person in the day's list, for the assertions above. */
    private SeatingPersonResponse onlyRow(BadgeAction action) {
        return service.dayMovements(LAST_SUNDAY, action, null, PageRequest.of(0, 20))
                .content().stream()
                .filter(r -> first.getBadgeNumber().equals(r.badgeNumber()))
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------------------ helpers

    private void signInAs(Role role, String designation, Set<Long> zoneIds) {
        Set<Zone> zones = zoneIds.stream()
                .map(id -> zoneRepository.findById(id).orElseThrow())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        SewadarRole held = designation == null ? null
                : sewadarRoleRepository.findByNameIgnoreCase(designation)
                        .orElseGet(() -> sewadarRoleRepository.save(
                                SewadarRole.builder().name(designation).active(true).build()));

        User user = userRepository.save(User.builder()
                .username("ws-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName(role.getDisplayName())
                .role(role)
                .sewadarRole(held)
                .zones(zones)
                .enabled(true)
                .build());

        AppUserPrincipal principal = new AppUserPrincipal(user, null, designation);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

package com.user.management;

import com.user.management.entity.Attendance;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Role;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rounding the office does by hand: twenty past counts as the hour, twenty to
 * counts as the next one. Half past is the line, and it rounds up.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EffectiveHoursTest {

    @Autowired ReportService reportService;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final LocalDate DAY = LocalDate.now().withDayOfMonth(1);

    private Zone zone;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("EH").name("Hours").active(true).build());
        signInAsAdmin();
    }

    @Test
    @DisplayName("1:20 counts as one hour, 1:38 as two")
    void roundsAtTheHalfHour() {
        worked("H-1", LocalTime.of(9, 0), LocalTime.of(10, 20));   // 1:20
        worked("H-2", LocalTime.of(9, 0), LocalTime.of(10, 38));   // 1:38
        worked("H-3", LocalTime.of(9, 0), LocalTime.of(10, 30));   // 1:30, the line

        MonthlyReportResponse report = reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, null, null);

        assertThat(report.rows()).extracting(r -> r.badgeNumber() + "=" + r.effectiveHours())
                .contains("H-1=1", "H-2=2", "H-3=2");
    }

    @Test
    @DisplayName("the hours themselves are kept exactly, for the HH:MM the screen shows")
    void theExactTimeSurvives() {
        worked("H-4", LocalTime.of(9, 0), LocalTime.of(17, 45));   // 8:45

        MonthlyReportResponse report = reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, null, null);

        assertThat(report.rows()).singleElement().satisfies(row -> {
            assertThat(row.totalHours()).isEqualTo(8.75);
            assertThat(row.effectiveHours()).isEqualTo(9);
        });
    }

    @Test
    @DisplayName("the total is the rounded values added, so the column adds up")
    void theTotalAddsTheColumn() {
        worked("H-5", LocalTime.of(9, 0), LocalTime.of(10, 20));   // 1:20 -> 1
        worked("H-6", LocalTime.of(9, 0), LocalTime.of(10, 20));   // 1:20 -> 1

        MonthlyReportResponse report = reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, null, null);

        // 1 + 1, not round(2:40) = 3.
        assertThat(report.totals().effectiveHours()).isEqualTo(2);
    }

    @Test
    @DisplayName("somebody who did no sewa all month is on the sheet at zero")
    void nothingDoneIsStillARow() {
        worked("H-7", LocalTime.of(9, 0), LocalTime.of(17, 0));
        // No attendance at all for this one, anywhere in the month.
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber("H-8").name("Did Nothing").zone(zone).build());

        MonthlyReportResponse report = reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, null, null);

        /*
         * Half of what the office reads the sheet for is who did not turn up, so a
         * month with no attendance is a row of zeros, not a missing name.
         */
        assertThat(report.rows()).extracting(r -> r.badgeNumber() + "=" + r.effectiveHours())
                .contains("H-7=8", "H-8=0");
        assertThat(report.rows()).filteredOn(r -> "H-8".equals(r.badgeNumber()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.presentDays()).isZero();
                    assertThat(row.totalHours()).isZero();
                    assertThat(row.zoneName()).isEqualTo("Hours");
                });
    }

    @Test
    @DisplayName("the point on the report is the satsang point, the one the form fills in")
    void theReportCarriesTheSatsangPoint() {
        Sewadar sewadar = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("H-9").name("Has A Point").zone(zone)
                .area("Indore").centerPoint("Nanda Nagar").build());
        worked(sewadar, LocalTime.of(9, 0), LocalTime.of(17, 0));

        MonthlyReportResponse report = reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, null, null);

        /*
         * It used to read the sewa point, which is a different list - and an empty
         * one here, because the sewa point came off the Add Sewadar form. The column
         * showed a dash against every name.
         */
        assertThat(report.rows()).filteredOn(r -> "H-9".equals(r.badgeNumber()))
                .singleElement()
                .satisfies(row -> assertThat(row.satsangPoint()).isEqualTo("Nanda Nagar"));
    }

    // ------------------------------------------------------------------ helpers

    private void worked(String badge, LocalTime in, LocalTime out) {
        Sewadar sewadar = sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge).name("Sewadar " + badge).zone(zone).build());
        worked(sewadar, in, out);
    }

    private void worked(Sewadar sewadar, LocalTime in, LocalTime out) {
        Attendance attendance = Attendance.builder()
                .sewadar(sewadar)
                .zone(zone)
                .attendanceDate(DAY)
                .sewaType(SewaType.DAILY_SEWA)
                .status(AttendanceStatus.PRESENT)
                .inTime(in)
                .outTime(out)
                .build();
        attendance.recalculateHours();
        attendanceRepository.save(attendance);
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("hours-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Hours Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

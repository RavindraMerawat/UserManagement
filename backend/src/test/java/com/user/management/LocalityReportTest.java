package com.user.management;

import com.user.management.entity.Locality;
import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.MonthlyReportResponse;
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
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The monthly report, narrowed to Local or Outstation.
 *
 * <p>The office prints the local register almost every time, so the screen offers
 * Local first - but the server has no default of its own. A request without a
 * locality still covers everyone, which is what every other caller and every older
 * link expects.</p>
 *
 * <p>The third case is the one to know about: a sewadar whose locality has never
 * been filled in is neither Local nor Outstation, so a Local report leaves them out.
 * That is the honest answer - they have not been recorded as local - and it is
 * pinned here so it is a decision rather than a surprise.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalityReportTest {

    @Autowired ReportService reportService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final LocalDate DAY = LocalDate.now().withDayOfMonth(1);

    private Zone zone;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("LC").name("Locality").active(true).build());
        person("L-001", "Asha Local", Locality.LOCAL);
        person("L-002", "Bina Local", Locality.LOCAL);
        person("O-001", "Chitra Outstation", Locality.OUTSTATION);
        person("N-001", "Devi Unrecorded", null);
        signInAsAdmin();
    }

    @Test
    @DisplayName("Local gives the local sewadars, and says so in the title")
    void localOnly() {
        MonthlyReportResponse report = report(Locality.LOCAL);

        assertThat(report.rows()).extracting(row -> row.badgeNumber())
                .containsExactlyInAnyOrder("L-001", "L-002");
        assertThat(report.title()).endsWith(" - Local");
    }

    @Test
    @DisplayName("Outstation gives the ones who travel in")
    void outstationOnly() {
        MonthlyReportResponse report = report(Locality.OUTSTATION);

        assertThat(report.rows()).extracting(row -> row.badgeNumber()).containsExactly("O-001");
        assertThat(report.title()).endsWith(" - Outstation");
    }

    @Test
    @DisplayName("no locality means everyone, including the sewadar whose locality is blank")
    void noLocalityCoversEveryone() {
        MonthlyReportResponse report = report(null);

        assertThat(report.rows()).extracting(row -> row.badgeNumber())
                .containsExactlyInAnyOrder("L-001", "L-002", "O-001", "N-001");
        assertThat(report.title()).doesNotContain(" - Local");
    }

    @Test
    @DisplayName("a blank locality is not counted as Local")
    void blankIsNotLocal() {
        assertThat(report(Locality.LOCAL).rows()).extracting(row -> row.badgeNumber())
                .doesNotContain("N-001");
    }

    // ------------------------------------------------------------------ helpers

    private MonthlyReportResponse report(Locality locality) {
        return reportService.monthly(YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, null, locality);
    }

    private void person(String badge, String name, Locality locality) {
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge).name(name).zone(zone).locality(locality).build());
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("locality-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Locality Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

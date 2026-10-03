package com.user.management;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.SewadarResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.ReportService;
import com.user.management.service.SewadarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Searching and reporting by designation.
 *
 * <p>The office asks "how many hours have the co-ordinators done" and "show me the
 * supervisors". Until now both meant generating everything and reading past the rows
 * that were not wanted.</p>
 *
 * <p>The case worth pinning is the third one: a sewadar whose designation has never
 * been filled in must still appear when no designation is asked for. Written as an
 * inner join - the obvious way - those records vanish from the screen entirely, and
 * on a register loaded from a spreadsheet there are a great many of them.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DesignationFilterTest {

    @Autowired SewadarService sewadarService;
    @Autowired ReportService reportService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired SewadarRoleRepository sewadarRoleRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final LocalDate DAY = LocalDate.now().withDayOfMonth(1);

    private Zone zone;
    private SewadarRole coordinator;
    private SewadarRole supervisor;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("DF").name("Designations").active(true).build());
        coordinator = designation("Co-ordinator");
        supervisor = designation("Supervisor");

        person("D-001", "Asha Coordinator", coordinator);
        person("D-002", "Bina Coordinator", coordinator);
        person("D-003", "Chitra Supervisor", supervisor);
        person("D-004", "Devi Unassigned", null);

        signInAsAdmin();
    }

    // --------------------------------------------------------------- the search

    @Test
    @DisplayName("the sewadar search returns only that designation")
    void searchByDesignation() {
        assertThat(sewadarService.search(null, null, coordinator.getId(), null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("D-001", "D-002");

        assertThat(sewadarService.search(null, null, supervisor.getId(), null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("D-003");
    }

    @Test
    @DisplayName("the designation narrows a text search rather than replacing it")
    void searchCombinesWithTheText() {
        assertThat(sewadarService.search("asha", null, coordinator.getId(), null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("D-001");

        // Asha is not a supervisor, so the two together find nobody.
        assertThat(sewadarService.search("asha", null, supervisor.getId(), null, null, page()).content()).isEmpty();
    }

    @Test
    @DisplayName("a text match on the badge number still obeys the designation filter")
    void theTextSearchNarrowsWithTheDesignation() {
        /*
         * The text looks in seven fields and the designation sits beside it, so the two
         * have to meet rather than compete. "D-00" is in every badge number here, which
         * is how the office searches - a filter that lost to the text would hand back
         * all four people whichever designation was chosen.
         */
        assertThat(sewadarService.search("d-00", null, supervisor.getId(), null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("D-003");
    }

    @Test
    @DisplayName("a sewadar with no designation is still in an unfiltered search")
    void unfilteredSearchKeepsTheUnassigned() {
        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("D-001", "D-002", "D-003", "D-004");
    }

    @Test
    @DisplayName("an id that is not a designation is refused, not answered with an empty list")
    void unknownDesignationIsRefused() {
        assertThatThrownBy(() -> sewadarService.search(null, null, 999_999L, null, null, page()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No designation");
    }

    // --------------------------------------------------------------- the report

    @Test
    @DisplayName("the monthly report holds only that designation, and says so in its title")
    void monthlyReportByDesignation() {
        MonthlyReportResponse report = reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, coordinator.getId(), null);

        assertThat(report.rows()).extracting(row -> row.badgeNumber())
                .containsExactlyInAnyOrder("D-001", "D-002");
        assertThat(report.totals().sewadarCount()).isEqualTo(2);
        // The title is what the screen shows and what the download is named after.
        assertThat(report.title()).endsWith(" - Co-ordinator");
    }

    @Test
    @DisplayName("without a designation the report still covers everyone, assigned or not")
    void monthlyReportWithoutADesignation() {
        MonthlyReportResponse report = reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, null, null);

        assertThat(report.rows()).extracting(row -> row.badgeNumber())
                .containsExactlyInAnyOrder("D-001", "D-002", "D-003", "D-004");
        assertThat(report.title()).doesNotContain(" - Co-ordinator");
    }

    @Test
    @DisplayName("an id that is not a designation is refused by the report too")
    void reportRefusesAnUnknownDesignation() {
        assertThatThrownBy(() -> reportService.monthly(
                YearMonth.from(DAY).getYear(), YearMonth.from(DAY).getMonthValue(),
                null, null, null, 999_999L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No designation");
    }

    // ------------------------------------------------------------------ helpers

    private Pageable page() {
        return PageRequest.of(0, 50, Sort.by("name"));
    }

    private SewadarRole designation(String name) {
        return sewadarRoleRepository.findByNameIgnoreCase(name).orElseGet(() ->
                sewadarRoleRepository.save(SewadarRole.builder().name(name).active(true).build()));
    }

    private void person(String badge, String name, SewadarRole role) {
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge).name(name).zone(zone).role(role).build());
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("designation-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Designation Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

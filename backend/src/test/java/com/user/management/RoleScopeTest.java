package com.user.management;

import com.user.management.entity.Attendance;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Role;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.AttendanceRequest;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.PageResponse;
import com.user.management.model.SewadarResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.AttendanceService;
import com.user.management.service.ReportService;
import com.user.management.service.SewadarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rule that matters most in this application: what each role is allowed to see.
 * Two zones, two sewadars and one attendance entry each, then the same query run as
 * five different roles.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleScopeTest {

    @Autowired SewadarService sewadarService;
    @Autowired AttendanceService attendanceService;
    @Autowired ReportService reportService;
    @Autowired ZoneRepository zoneRepository;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone north;
    private Zone south;
    private Sewadar northSewadar;
    private Sewadar southSewadar;

    private static final LocalDate DAY = LocalDate.now().withDayOfMonth(1);

    @BeforeEach
    void seed() {
        north = zoneRepository.save(Zone.builder().code("N").name("North").active(true).build());
        south = zoneRepository.save(Zone.builder().code("S").name("South").active(true).build());

        northSewadar = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("N-001").name("North Sewadar").zone(north).active(true).build());
        southSewadar = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("S-001").name("South Sewadar").zone(south).active(true).build());

        attendanceRepository.save(Attendance.builder()
                .sewadar(northSewadar).zone(north).attendanceDate(DAY)
                .sewaType(SewaType.ROSTER_SEWA).status(AttendanceStatus.PRESENT).build());
        attendanceRepository.save(Attendance.builder()
                .sewadar(southSewadar).zone(south).attendanceDate(DAY)
                .sewaType(SewaType.CONSTRUCTION_SEWA).status(AttendanceStatus.PRESENT).build());
    }

    @Test
    @DisplayName("Admin sees sewadars and attendance from every zone")
    void adminSeesEverything() {
        signInAs(Role.ADMIN, Set.of(), null);

        PageResponse<SewadarResponse> sewadars = sewadarService.search(null, null, true, page());
        assertThat(sewadars.content()).extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("N-001", "S-001");

        assertThat(attendanceService.search(null, null, null, null, null, null, attendancePage()).content())
                .hasSize(2);
    }

    @Test
    @DisplayName("Office User sees every zone but cannot change sewadar master data")
    void officeUserIsReadOnlyAcrossZones() {
        signInAs(Role.OFFICE_USER, Set.of(), null);

        assertThat(sewadarService.search(null, null, true, page()).content()).hasSize(2);

        assertThatThrownBy(() -> sewadarService.delete(northSewadar.getId()))
                .hasMessageContaining("cannot add, edit or delete");
    }

    @Test
    @DisplayName("Zone Incharge sees only the zones assigned to the account")
    void zoneInchargeIsLimitedToItsZones() {
        signInAs(Role.ZONE_INCHARGE, Set.of(north.getId()), null);

        assertThat(sewadarService.search(null, null, true, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("N-001");

        assertThat(attendanceService.search(null, null, null, null, null, null, attendancePage()).content())
                .hasSize(1);

        // Reaching into another zone is refused rather than silently returning nothing.
        assertThatThrownBy(() -> sewadarService.search(null, south.getId(), true, page()))
                .hasMessageContaining("do not have access to zone");
    }

    @Test
    @DisplayName("Co-ordinator sees only its zones and may mark attendance there")
    void coordinatorIsLimitedToItsZones() {
        signInAs(Role.COORDINATOR, Set.of(south.getId()), null);

        assertThat(sewadarService.search(null, null, true, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("S-001");

        // Marking is allowed inside the zone the co-ordinator covers.
        AttendanceResponse marked = attendanceService.mark(new AttendanceRequest(
                southSewadar.getId(), DAY.plusDays(1), SewaType.OFFICE_SEWA,
                AttendanceStatus.PRESENT, null, null, null, "marked by co-ordinator"));
        assertThat(marked.statusLabel()).isEqualTo("Present");

        // ...and refused outside it.
        assertThatThrownBy(() -> attendanceService.mark(new AttendanceRequest(
                northSewadar.getId(), DAY.plusDays(1), SewaType.OFFICE_SEWA,
                AttendanceStatus.PRESENT, null, null, null, null)))
                .hasMessageContaining("do not have access to zone");

        // A co-ordinator does not own sewadar master data.
        assertThatThrownBy(() -> sewadarService.delete(southSewadar.getId()))
                .hasMessageContaining("cannot add, edit or delete");
    }

    @Test
    @DisplayName("Supervisor cannot open a sewadar from a zone it does not supervise")
    void supervisorCannotReadOtherZones() {
        signInAs(Role.SUPERVISOR, Set.of(south.getId()), null);

        assertThat(sewadarService.get(southSewadar.getId()).badgeNumber()).isEqualTo("S-001");

        assertThatThrownBy(() -> sewadarService.get(northSewadar.getId()))
                .hasMessageContaining("do not have access");
    }

    @Test
    @DisplayName("Sewadar sees only their own attendance, never another sewadar")
    void sewadarSeesOnlyItself() {
        signInAs(Role.SEWADAR, Set.of(), northSewadar.getId());

        List<SewadarResponse> visible = sewadarService.search(null, null, true, page()).content();
        assertThat(visible).extracting(SewadarResponse::badgeNumber).containsExactly("N-001");

        assertThat(attendanceService.search(null, null, null, null, null, null, attendancePage()).content())
                .hasSize(1);

        assertThatThrownBy(() -> attendanceService.search(southSewadar.getId(), null, null, null,
                null, null, attendancePage()))
                .hasMessageContaining("only view your own attendance");

        assertThatThrownBy(() -> sewadarService.get(southSewadar.getId()))
                .hasMessageContaining("do not have access");
    }

    @Test
    @DisplayName("The monthly report is aggregated inside the caller's scope")
    void monthlyReportRespectsScope() {
        YearMonth month = YearMonth.from(DAY);

        signInAs(Role.ADMIN, Set.of(), null);
        MonthlyReportResponse all = reportService.monthly(
                month.getYear(), month.getMonthValue(), null, null, null);
        assertThat(all.rows()).hasSize(2);
        assertThat(all.totals().presentDays()).isEqualTo(2);
        assertThat(all.totals().rosterSewaDays()).isEqualTo(1);
        assertThat(all.totals().constructionSewaDays()).isEqualTo(1);

        signInAs(Role.SEWADAR, Set.of(), northSewadar.getId());
        MonthlyReportResponse mine = reportService.monthly(
                month.getYear(), month.getMonthValue(), null, null, null);
        assertThat(mine.rows()).hasSize(1);
        assertThat(mine.rows().getFirst().badgeNumber()).isEqualTo("N-001");
        assertThat(mine.totals().presentDays()).isEqualTo(1);
    }

    // ---- helpers ----

    private PageRequest page() {
        return PageRequest.of(0, 20, Sort.by("name"));
    }

    private PageRequest attendancePage() {
        return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "attendanceDate"));
    }

    /** Puts a principal for the given role into the security context. */
    private void signInAs(Role role, Set<Long> zoneIds, Long sewadarId) {
        Set<Zone> zones = zoneIds.stream()
                .map(id -> zoneRepository.findById(id).orElseThrow())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        User user = userRepository.save(User.builder()
                .username(role.name().toLowerCase() + "-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName(role.getDisplayName())
                .role(role)
                .zones(zones)
                .enabled(true)
                .build());

        AppUserPrincipal principal = new AppUserPrincipal(user, sewadarId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

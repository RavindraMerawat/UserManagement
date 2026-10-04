package com.user.management;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import com.user.management.entity.SewaType;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.AttendanceRequest;
import com.user.management.model.AttendanceResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.AttendanceService;
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
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finding one person on All Attendance Record.
 *
 * <p>The screen had a date range, a zone, a sewa type and a status, and no way to
 * ask for a person - which is what the office actually wants from it: somebody is
 * at the desk saying their day is wrong. One box over the three things that
 * identify them, because which one is to hand varies: the GR. No on the badge, the
 * name they give, or the mobile the office has.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendanceSearchTest {

    private static final LocalDate DAY = LocalDate.now();

    @Autowired AttendanceService attendanceService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone zone;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("AS").name("Search").active(true).build());
        signInAsAdmin();
        markedToday("L00111", "Asha Rawal", "9820000001");
        markedToday("L00222", "Bina Devi", "9820000002");
        markedToday("L00333", "Chitra Asha", "9876500003");
    }

    @Test
    @DisplayName("by GR. No")
    void byBadgeNumber() {
        assertThat(found("L00222")).containsExactly("L00222");
    }

    @Test
    @DisplayName("by name, and a part of it is enough")
    void byName() {
        assertThat(found("bina")).containsExactly("L00222");
        // "Asha" is Asha Rawal's first name and Chitra Asha's second.
        assertThat(found("asha")).containsExactlyInAnyOrder("L00111", "L00333");
    }

    @Test
    @DisplayName("by mobile number")
    void byMobile() {
        assertThat(found("98765")).containsExactly("L00333");
    }

    @Test
    @DisplayName("case and surrounding spaces do not matter")
    void caseAndSpaces() {
        assertThat(found("  BINA  ")).containsExactly("L00222");
    }

    @Test
    @DisplayName("no search is no filter, not an empty screen")
    void noSearch() {
        assertThat(found(null)).containsExactlyInAnyOrder("L00111", "L00222", "L00333");
        assertThat(found("   ")).containsExactlyInAnyOrder("L00111", "L00222", "L00333");
    }

    @Test
    @DisplayName("somebody who is not there gives nothing, not everything")
    void noMatch() {
        assertThat(found("L99999")).isEmpty();
    }

    @Test
    @DisplayName("the search narrows the other filters rather than replacing them")
    void itCombinesWithTheOtherFilters() {
        assertThat(attendanceService.search(null, zone.getId(), SewaType.DAILY_SEWA,
                        AttendanceStatus.PRESENT, DAY, DAY, "bina", page()).content())
                .extracting(AttendanceResponse::badgeNumber)
                .containsExactly("L00222");

        // Bina is present, so asking for her absent days finds nothing.
        assertThat(attendanceService.search(null, null, null, AttendanceStatus.ABSENT,
                        null, null, "bina", page()).content())
                .isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private java.util.List<String> found(String query) {
        return attendanceService.search(null, null, null, null, null, null, query, page())
                .content().stream().map(AttendanceResponse::badgeNumber).toList();
    }

    private Pageable page() {
        return PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "attendanceDate").and(Sort.by("id")));
    }

    private void markedToday(String badge, String name, String mobile) {
        Sewadar person = sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge).name(name).mobile(mobile).zone(zone).gender(Gender.FEMALE)
                .build());
        attendanceService.mark(new AttendanceRequest(person.getId(), DAY, SewaType.DAILY_SEWA,
                AttendanceStatus.PRESENT, LocalTime.of(9, 0), LocalTime.of(17, 0), null, null));
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("att-search-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Search Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

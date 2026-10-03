package com.user.management;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.SewadarResponse;
import com.user.management.model.UserResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.SewadarService;
import com.user.management.service.UserService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three Badge Detail lists, and the filters behind them.
 *
 * <p>Badge Detail opens one of three lists - Issued, Received and Pending - and each
 * is the same sewadar search with the two badge flags set. Before this they were all
 * the same list: the screen asked for the filter, the filter reached the query, and
 * the query threw it away.</p>
 *
 * <p>The three lists are pinned here one by one, and so is the case that keeps them
 * honest: searching <i>inside</i> an open list has to stay inside it. The office
 * types part of a badge number, every badge number in a zone shares that part, and a
 * search that outran the filter would quietly hand back the whole register under the
 * heading "Pending".</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BadgeFilterTest {

    @Autowired SewadarService sewadarService;
    @Autowired UserService userService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone zone;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("BF").name("Badges").active(true).build());
        person("B-001", "Issued Not Back", true, false);
        person("B-002", "Issued And Back", true, true);
        person("B-003", "Never Issued", false, false);
        signInAsAdmin();
    }

    @Test
    @DisplayName("Issued lists the badges that went out and have not come back")
    void issued() {
        assertThat(sewadarService.search(null, null, null, true, false, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("B-001");
    }

    @Test
    @DisplayName("Received lists the badges that have come back")
    void received() {
        assertThat(sewadarService.search(null, null, null, null, true, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("B-002");
    }

    @Test
    @DisplayName("Pending lists the badges never issued")
    void pending() {
        assertThat(sewadarService.search(null, null, null, false, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("B-003");
    }

    @Test
    @DisplayName("no badge filter is no badge filter, not an empty list")
    void unfiltered() {
        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("B-001", "B-002", "B-003");
    }

    @Test
    @DisplayName("searching inside one of the lists stays inside it")
    void theTextSearchDoesNotEscapeTheBadgeFilter() {
        // "B-00" is in all three badge numbers: the text finds everyone, the open
        // list is Pending, and only the person in both comes back.
        assertThat(sewadarService.search("b-00", null, null, false, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactly("B-003");
    }

    @Test
    @DisplayName("the same holds on the accounts grid: a name match obeys the status filter")
    void theAccountSearchDoesNotEscapeTheStatusFilter() {
        account("badge-live", "Common Name One", true);
        account("badge-closed", "Common Name Two", false);

        assertThat(userService.search("common name", null, null, false,
                PageRequest.of(0, 50, Sort.by("username"))).content())
                .extracting(UserResponse::username)
                .containsExactly("badge-closed");
    }

    // ------------------------------------------------------------------ helpers

    private Pageable page() {
        return PageRequest.of(0, 50, Sort.by("name"));
    }

    private void person(String badge, String name, boolean issued, boolean received) {
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge).name(name).zone(zone)
                .badgeIssued(issued).badgeReceived(received).build());
    }

    private void account(String username, String fullName, boolean enabled) {
        userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName(fullName)
                .role(Role.SEWADAR)
                .enabled(enabled)
                .build());
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("badges-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Badge Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

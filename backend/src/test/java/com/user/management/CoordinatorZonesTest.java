package com.user.management;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.CreateUserRequest;
import com.user.management.security.AppUserDetailsService;
import com.user.management.security.AppUserPrincipal;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.service.UserService;
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

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A co-ordinator recorded as covering two zones sees both.
 *
 * <p>The reach is written on their sewadar record; without this it would be written
 * down and mean nothing, which is the failure that hides - the screen says two zones
 * and the data shows one.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoordinatorZonesTest {

    @Autowired AppUserDetailsService userDetailsService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired SewadarRoleRepository sewadarRoleRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserService userService;

    private Zone north;
    private Zone south;
    private Zone east;

    @BeforeEach
    void seed() {
        north = zoneRepository.save(Zone.builder().code("CZ-N").name("North").active(true).build());
        south = zoneRepository.save(Zone.builder().code("CZ-S").name("South").active(true).build());
        east = zoneRepository.save(Zone.builder().code("CZ-E").name("East").active(true).build());
    }

    @Test
    @DisplayName("the login reaches the zones the record covers, as well as its own")
    void theLoginReachesTheCoveredZones() {
        AppUserPrincipal principal = signedIn(north, Set.of(south));

        assertThat(principal.getZoneIds())
                .containsExactlyInAnyOrder(north.getId(), south.getId());
    }

    @Test
    @DisplayName("no extra zones means the record's own zone, and no more")
    void withoutExtraZones() {
        AppUserPrincipal principal = signedIn(north, Set.of());

        assertThat(principal.getZoneIds()).containsExactly(north.getId());
    }

    @Test
    @DisplayName("zones on the account are kept, not replaced by the record's")
    void theAccountsOwnZonesSurvive() {
        Sewadar sewadar = coordinator(north, Set.of(south));
        User user = userRepository.save(User.builder()
                .username("cz-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Co-ordinator")
                .role(Role.COORDINATOR)
                .zones(new LinkedHashSet<>(Set.of(east)))   // given on the account
                .enabled(true)
                .build());
        sewadar.setUser(user);
        sewadarRepository.save(sewadar);

        AppUserPrincipal principal =
                (AppUserPrincipal) userDetailsService.loadUserByUsername(user.getUsername());

        assertThat(principal.getZoneIds())
                .containsExactlyInAnyOrder(east.getId(), north.getId(), south.getId());
    }

    @Test
    @DisplayName("an account made against a co-ordinator's record reaches the zones it covers")
    void anAccountMadeFromTheRecordCarriesItsZones() {
        Sewadar sewadar = coordinator(north, Set.of(south));
        signInAsAdmin();

        String username = "cz-made-" + System.nanoTime();
        userService.create(new CreateUserRequest(
                username,                       // username
                "Sewa@12345",                   // password
                "Two Zone Co-ordinator",        // fullName
                null,                           // email
                null,                           // mobile
                sewadar.getRole().getId(),      // roleId - the designation
                null,                           // badgeNumber - the GR. No this login is
                null,                           // gender - whose register it reads
                Role.COORDINATOR,               // role - the account type
                Set.of(north.getId()),          // zoneIds - what the account itself grants
                sewadar.getId(),                // sewadarId - the record it belongs to
                false));                        // mustChangePassword

        AppUserPrincipal principal =
                (AppUserPrincipal) userDetailsService.loadUserByUsername(username);

        // South is nowhere on the account; it is on the record, and that is the point.
        assertThat(principal.getZoneIds())
                .containsExactlyInAnyOrder(north.getId(), south.getId());
    }

    // ------------------------------------------------------------------ helpers

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("cz-admin-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Zone Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Sewadar coordinator(Zone own, Set<Zone> covers) {
        SewadarRole role = sewadarRoleRepository.findByNameIgnoreCase("Co-ordinator")
                .orElseGet(() -> sewadarRoleRepository.save(
                        SewadarRole.builder().name("Co-ordinator").active(true).build()));

        return sewadarRepository.save(Sewadar.builder()
                .badgeNumber("CZ-" + System.nanoTime())
                .name("Zone Co-ordinator")
                .zone(own)
                .extraZones(new LinkedHashSet<>(covers))
                .role(role)
                .build());
    }

    private AppUserPrincipal signedIn(Zone own, Set<Zone> covers) {
        Sewadar sewadar = coordinator(own, covers);
        User user = userRepository.save(User.builder()
                .username("cz-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Co-ordinator")
                .role(Role.COORDINATOR)
                .enabled(true)
                .build());
        sewadar.setUser(user);
        sewadarRepository.save(sewadar);

        return (AppUserPrincipal) userDetailsService.loadUserByUsername(user.getUsername());
    }
}

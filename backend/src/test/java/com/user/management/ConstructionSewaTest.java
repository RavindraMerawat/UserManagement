package com.user.management;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.model.ConstructionSewaRequest;
import com.user.management.model.ConstructionSewaResponse;
import com.user.management.repository.ConstructionSewaRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.ConstructionSewaService;
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

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The construction sewa register: a day counts once, the office records it, the zone
 * trio may only read, and nobody reaches outside their own zones.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConstructionSewaTest {

    @Autowired ConstructionSewaService service;
    @Autowired ConstructionSewaRepository repository;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired SewadarRoleRepository sewadarRoleRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private Zone north;
    private Zone south;
    private Sewadar northSewadar;
    private Sewadar southSewadar;

    @BeforeEach
    void seed() {
        north = zoneRepository.save(Zone.builder().code("CN").name("North").active(true).build());
        south = zoneRepository.save(Zone.builder().code("CS").name("South").active(true).build());
        northSewadar = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("C-100").name("North Sewadar").age(30).mobile("9000000001")
                .zone(north).build());
        southSewadar = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("C-200").name("South Sewadar").age(40).mobile("9000000002")
                .zone(south).build());
    }

    // ------------------------------------------------------ one day, counted once

    @Test
    @DisplayName("each day recorded is a row, and the count is how many rows there are")
    void eachDayIsARow() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());

        service.record(new ConstructionSewaRequest(northSewadar.getId(), TODAY, "Shed"));
        service.record(new ConstructionSewaRequest(northSewadar.getId(), YESTERDAY, null));

        assertThat(service.countFor(northSewadar.getId())).isEqualTo(2);
        assertThat(service.forSewadar(northSewadar.getId(), page()).content())
                .extracting(ConstructionSewaResponse::sewaDate)
                .containsExactly(TODAY, YESTERDAY);   // newest day first
    }

    @Test
    @DisplayName("the same day twice is refused, and says which day it was")
    void aDayCountsOnce() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
        service.record(new ConstructionSewaRequest(northSewadar.getId(), TODAY, null));

        assertThatThrownBy(() -> service.record(
                new ConstructionSewaRequest(northSewadar.getId(), TODAY, "again")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already recorded")
                .hasMessageContaining(TODAY.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-uuuu")));

        assertThat(service.countFor(northSewadar.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("the same day for two sewadars is two entries, not a clash")
    void theRuleIsPerSewadar() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
        service.record(new ConstructionSewaRequest(northSewadar.getId(), TODAY, null));
        service.record(new ConstructionSewaRequest(southSewadar.getId(), TODAY, null));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("a day removed can be recorded again")
    void removingFreesTheDay() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
        ConstructionSewaResponse entry =
                service.record(new ConstructionSewaRequest(northSewadar.getId(), TODAY, null));

        service.delete(entry.id());
        assertThat(service.countFor(northSewadar.getId())).isZero();

        service.record(new ConstructionSewaRequest(northSewadar.getId(), TODAY, null));
        assertThat(service.countFor(northSewadar.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("nothing recorded reads as an empty list, not as an error")
    void nothingRecordedYet() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
        assertThat(service.forSewadar(northSewadar.getId(), page()).content()).isEmpty();
        assertThat(service.countFor(northSewadar.getId())).isZero();
    }

    // ------------------------------------------------------------ who may write

    @Test
    @DisplayName("the office records a day")
    void theOfficeMayRecord() {
        LocalDate day = TODAY;
        for (String designation : new String[] { "Office Incharge", "Office Sewadar" }) {
            signInAs(Role.OFFICE_ADMIN, designation, Set.of());
            assertThat(service.record(new ConstructionSewaRequest(northSewadar.getId(), day, null))
                    .sewaDate()).isEqualTo(day);
            day = day.minusDays(1);
        }
    }

    @Test
    @DisplayName("the zone trio may read the register but not write to it")
    void theZoneTrioIsReadOnly() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
        service.record(new ConstructionSewaRequest(northSewadar.getId(), TODAY, null));

        for (String designation : new String[] { "Coordinator", "Zone Incharge", "Supervisor" }) {
            signInAs(Role.COORDINATOR, designation, Set.of(north.getId()));

            assertThat(service.forSewadar(northSewadar.getId(), page()).content())
                    .as("%s can read", designation)
                    .hasSize(1);

            assertThatThrownBy(() -> service.record(
                    new ConstructionSewaRequest(northSewadar.getId(), YESTERDAY, null)))
                    .as("%s cannot write", designation)
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("cannot record");
        }
    }

    // ---------------------------------------------------------------- the zones

    @Test
    @DisplayName("a zone-scoped role cannot read a sewadar outside its zones")
    void zoneScopedCannotReachOut() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
        service.record(new ConstructionSewaRequest(southSewadar.getId(), TODAY, null));

        signInAs(Role.ZONE_INCHARGE, "Zone Incharge", Set.of(north.getId()));
        assertThatThrownBy(() -> service.forSewadar(southSewadar.getId(), page()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("the office reads any zone")
    void officeReadsEveryZone() {
        signInAs(Role.ADMIN, "Office Incharge", Set.of());
        service.record(new ConstructionSewaRequest(southSewadar.getId(), TODAY, null));

        assertThat(service.forSewadar(southSewadar.getId(), page()).content())
                .extracting(ConstructionSewaResponse::badgeNumber)
                .containsExactly("C-200");
    }

    // ------------------------------------------------------------------ helpers

    private PageRequest page() {
        return PageRequest.of(0, 20);
    }

    private void signInAs(Role role, String designation, Set<Long> zoneIds) {
        Set<Zone> zones = zoneIds.stream()
                .map(id -> zoneRepository.findById(id).orElseThrow())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        SewadarRole held = designation == null ? null
                : sewadarRoleRepository.findByNameIgnoreCase(designation)
                        .orElseGet(() -> sewadarRoleRepository.save(
                                SewadarRole.builder().name(designation).active(true).build()));

        User user = userRepository.save(User.builder()
                .username("cs-" + System.nanoTime())
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

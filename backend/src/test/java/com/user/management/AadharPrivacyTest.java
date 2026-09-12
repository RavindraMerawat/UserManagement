package com.user.management;

import com.user.management.entity.Role;
import com.user.management.entity.SewaType;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.SewadarLookupResponse;
import com.user.management.model.SewadarRequest;
import com.user.management.model.SewadarResponse;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AadharMask;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.CheckInOutService;
import com.user.management.service.SewadarService;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aadhaar numbers are personal data, and what protects them here is <b>masking by
 * role</b>: only the roles that register and correct a number ever receive all twelve
 * digits, and the masking happens on the server, so a masked response never carries
 * the other eight.
 *
 * <p>The number itself is stored as typed. Encryption at rest was built and then
 * deliberately dropped - see change set 11 in the CHANGELOG for what that decision
 * costs and what it buys.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AadharPrivacyTest {

    private static final String AADHAR = "123456789012";

    @Autowired SewadarService sewadarService;
    @Autowired CheckInOutService checkInOutService;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone north;
    private Zone south;

    @BeforeEach
    void seed() {
        north = zoneRepository.save(Zone.builder().code("N").name("North").active(true).build());
        south = zoneRepository.save(Zone.builder().code("S").name("South").active(true).build());
    }

    @Test
    @DisplayName("Admin and Office Admin read the whole number; a zone role gets the mask")
    void maskingFollowsTheRole() {
        signInAs(Role.ADMIN, Set.of(), null);
        Long id = sewadarService.create(request("A-001", "Amrit Kaur", north.getId(), AADHAR)).id();

        SewadarResponse asAdmin = sewadarService.get(id);
        assertThat(asAdmin.aadharNumber()).isEqualTo(AADHAR);
        assertThat(asAdmin.aadharMasked()).isFalse();

        signInAs(Role.OFFICE_ADMIN, Set.of(), null);
        assertThat(sewadarService.get(id).aadharNumber()).isEqualTo(AADHAR);

        signInAs(Role.ZONE_INCHARGE, Set.of(north.getId()), null);
        SewadarResponse asZoneIncharge = sewadarService.get(id);
        assertThat(asZoneIncharge.aadharMasked()).isTrue();
        assertThat(asZoneIncharge.aadharNumber()).isEqualTo("XXXX XXXX 9012");
        // Only the last four digits leave the server for this role.
        assertThat(asZoneIncharge.aadharNumber()).doesNotContain("12345678");

        signInAs(Role.OFFICE_USER, Set.of(), null);
        assertThat(sewadarService.get(id).aadharNumber()).isEqualTo("XXXX XXXX 9012");

        signInAs(Role.SUPERVISOR, Set.of(north.getId()), null);
        assertThat(sewadarService.get(id).aadharNumber()).isEqualTo("XXXX XXXX 9012");
    }

    @Test
    @DisplayName("The grid is masked too, not just the detail view")
    void theListIsMaskedAsWell() {
        signInAs(Role.ADMIN, Set.of(), null);
        sewadarService.create(request("A-001", "Amrit Kaur", north.getId(), AADHAR));

        signInAs(Role.ZONE_INCHARGE, Set.of(north.getId()), null);
        List<SewadarResponse> rows = sewadarService.search(null, null, true, org.springframework.data.domain.PageRequest
                .of(0, 20, org.springframework.data.domain.Sort.by("name"))).content();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().aadharNumber()).isEqualTo("XXXX XXXX 9012");
        assertThat(rows.getFirst().aadharMasked()).isTrue();
    }

    @Test
    @DisplayName("A Sewadar sees their own number in full")
    void sewadarSeesOwnNumberInFull() {
        signInAs(Role.ADMIN, Set.of(), null);
        Long id = sewadarService.create(request("A-001", "Amrit Kaur", north.getId(), AADHAR)).id();

        signInAs(Role.SEWADAR, Set.of(), id);
        SewadarResponse own = sewadarService.get(id);
        assertThat(own.aadharNumber()).isEqualTo(AADHAR);
        assertThat(own.aadharMasked()).isFalse();
    }

    @Test
    @DisplayName("A row with no number is never reported as masked")
    void missingNumberIsNotMasked() {
        signInAs(Role.ADMIN, Set.of(), null);
        Long id = sewadarService.create(request("A-003", "No Aadhaar Yet", north.getId(), null)).id();

        signInAs(Role.SUPERVISOR, Set.of(north.getId()), null);
        SewadarResponse response = sewadarService.get(id);
        assertThat(response.aadharNumber()).isNull();
        assertThat(response.aadharMasked()).isFalse();
    }

    @Test
    @DisplayName("The attendance card is masked for a role that may not read the number")
    void theAttendanceCardIsMaskedToo() {
        signInAs(Role.ADMIN, Set.of(), null);
        sewadarService.create(request("A-001", "Amrit Kaur", north.getId(), AADHAR));
        // A second row in another zone, to prove the hit is the right one.
        sewadarService.create(request("B-001", "Other Person", south.getId(), "999988887777"));

        List<SewadarLookupResponse> asAdmin =
                checkInOutService.lookup(AADHAR, SewaType.ROSTER_SEWA, null);
        assertThat(asAdmin).extracting(SewadarLookupResponse::badgeNumber).containsExactly("A-001");
        assertThat(asAdmin.getFirst().aadharNumber()).isEqualTo(AADHAR);
        assertThat(asAdmin.getFirst().aadharMasked()).isFalse();

        signInAs(Role.SUPERVISOR, Set.of(north.getId()), null);
        List<SewadarLookupResponse> asSupervisor =
                checkInOutService.lookup(AADHAR, SewaType.ROSTER_SEWA, null);
        assertThat(asSupervisor).hasSize(1);
        assertThat(asSupervisor.getFirst().aadharMasked()).isTrue();
        assertThat(asSupervisor.getFirst().aadharNumber()).isEqualTo("XXXX XXXX 9012");
    }

    @Test
    @DisplayName("A card scan still resolves to one person, however the number is typed")
    void lookupFindsTheNumber() {
        signInAs(Role.ADMIN, Set.of(), null);
        sewadarService.create(request("A-001", "Amrit Kaur", north.getId(), AADHAR));

        assertThat(checkInOutService.lookup(AADHAR, SewaType.ROSTER_SEWA, null))
                .extracting(SewadarLookupResponse::badgeNumber).containsExactly("A-001");

        // Formatted the way it is printed on the card.
        assertThat(checkInOutService.lookup("1234 5678 9012", SewaType.ROSTER_SEWA, null))
                .extracting(SewadarLookupResponse::badgeNumber).containsExactly("A-001");

        // A partial number is not an Aadhaar and must not match one.
        assertThat(checkInOutService.lookup("12345678", SewaType.ROSTER_SEWA, null)).isEmpty();
    }

    @Test
    @DisplayName("A duplicate Aadhaar is refused, however it is formatted")
    void duplicateIsRefused() {
        signInAs(Role.ADMIN, Set.of(), null);
        sewadarService.create(request("A-001", "Amrit Kaur", north.getId(), AADHAR));

        assertThatThrownBy(() -> sewadarService.create(
                request("A-002", "Someone Else", north.getId(), "1234 5678 9012")))
                .hasMessageContaining("already registered to another sewadar");
    }

    @Test
    @DisplayName("Saving a masked value back is refused instead of overwriting the number")
    void maskedValueCannotBeSavedBack() {
        signInAs(Role.ADMIN, Set.of(), null);
        Long id = sewadarService.create(request("A-001", "Amrit Kaur", north.getId(), AADHAR)).id();

        assertThatThrownBy(() -> sewadarService.update(id,
                request("A-001", "Amrit Kaur", north.getId(), "XXXX XXXX 9012")))
                .hasMessageContaining("masked");

        assertThat(sewadarService.get(id).aadharNumber()).isEqualTo(AADHAR);
    }

    @Test
    @DisplayName("Masking keeps the last four digits and nothing else")
    void maskShowsOnlyTheLastFour() {
        assertThat(AadharMask.mask(AADHAR)).isEqualTo("XXXX XXXX 9012");
        assertThat(AadharMask.mask("1234 5678 9012")).isEqualTo("XXXX XXXX 9012");
        assertThat(AadharMask.mask(null)).isNull();
        assertThat(AadharMask.mask("  ")).isNull();
        assertThat(AadharMask.mask("12")).isEqualTo("XXXX XXXX XXXX");

        assertThat(AadharMask.looksMasked("XXXX XXXX 9012")).isTrue();
        assertThat(AadharMask.looksMasked(AADHAR)).isFalse();
        assertThat(AadharMask.looksMasked(null)).isFalse();
    }

    // ---- helpers ----

    private SewadarRequest request(String badge, String name, Long zoneId, String aadhar) {
        return new SewadarRequest(badge, false, name, null, null, null, zoneId, null,
                aadhar, null, null, null, null, null, null, null, null, null, null,
                true, false, null);
    }

    /** Puts a principal for the given role into the security context. */
    private void signInAs(Role role, Set<Long> zoneIds, Long sewadarId) {
        Set<Zone> zones = zoneIds.stream()
                .map(id -> zoneRepository.findById(id).orElseThrow())
                .collect(Collectors.toCollection(LinkedHashSet::new));

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

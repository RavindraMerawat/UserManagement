package com.user.management;

import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.SewadarResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An account reads its own gender's register.
 *
 * <p>The office asked for it in one line: a male login sees the male sewadars, a
 * female login the female ones, and the Admin sees both. The gender is on the
 * account - nothing else in the system carried one - and it narrows every role
 * except Admin, on top of whatever zone reach that role already had.</p>
 *
 * <p>The last case is the one that decided the design: an account with <b>no</b>
 * gender set sees both, exactly as it did before this existed. Every account in the
 * office was made before the field, so the other choice - show nothing until
 * somebody fills it in - would have emptied every screen on the day this shipped.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GenderScopeTest {

    @Autowired SewadarService sewadarService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone zone;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("GS").name("Gender").active(true).build());
        person("M-001", "Mohan Kumar", Gender.MALE);
        person("M-002", "Manoj Singh", Gender.MALE);
        person("F-001", "Meera Devi", Gender.FEMALE);
        person("F-002", "Nisha Rani", Gender.FEMALE);
        person("U-001", "Unrecorded Person", null);
    }

    @Test
    @DisplayName("a male login reads the male register")
    void maleSeesMale() {
        signInAs(Role.OFFICE_USER, "Office Sewadar", Gender.MALE);

        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("M-001", "M-002");
    }

    @Test
    @DisplayName("a female login reads the female register")
    void femaleSeesFemale() {
        signInAs(Role.OFFICE_USER, "Office Sewadar", Gender.FEMALE);

        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("F-001", "F-002");
    }

    @Test
    @DisplayName("the rule holds for a zone role too, on top of its zones")
    void itNarrowsAZoneRoleAsWell() {
        signInAs(Role.COORDINATOR, "Co-ordinator", Gender.FEMALE);

        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("F-001", "F-002");
    }

    @Test
    @DisplayName("Admin sees both registers, which is the exception the office asked for")
    void adminSeesEverybody() {
        signInAs(Role.ADMIN, null, Gender.MALE);

        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("M-001", "M-002", "F-001", "F-002", "U-001");
    }

    @Test
    @DisplayName("an account with no gender set sees both, as it did before the field existed")
    void noGenderOnTheAccountChangesNothing() {
        signInAs(Role.OFFICE_USER, "Office Sewadar", null);

        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber)
                .containsExactlyInAnyOrder("M-001", "M-002", "F-001", "F-002", "U-001");
    }

    @Test
    @DisplayName("a sewadar whose own gender is blank is in neither register")
    void theUnrecordedPersonIsInNeither() {
        signInAs(Role.OFFICE_USER, "Office Sewadar", Gender.MALE);
        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber).doesNotContain("U-001");

        signInAs(Role.OFFICE_USER, "Office Sewadar", Gender.FEMALE);
        assertThat(sewadarService.search(null, null, null, null, null, page()).content())
                .extracting(SewadarResponse::badgeNumber).doesNotContain("U-001");
    }

    // ------------------------------------------------------------------ helpers

    private Pageable page() {
        return PageRequest.of(0, 50, Sort.by("name"));
    }

    private void person(String badge, String name, Gender gender) {
        sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge).name(name).zone(zone).gender(gender).build());
    }

    private void signInAs(Role role, String designation, Gender gender) {
        User user = userRepository.save(User.builder()
                // The zone trio reads its own zones, so the account carries this one;
                // without it the zone scope would empty the result before gender did.
                .zones(new java.util.LinkedHashSet<>(java.util.Set.of(zone)))
                .username("gender-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Gender Scoped")
                .role(role)
                .gender(gender)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(user, null, designation);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

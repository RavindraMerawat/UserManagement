package com.user.management;

import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.CreateUserRequest;
import com.user.management.model.UserResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.UserService;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Making login accounts for people who have no email address.
 *
 * <p>Reported as *"This record conflicts with existing data (duplicate badge number,
 * zone code or attendance entry)"* while creating an account for GR. No L04678 - a
 * number that was on the register exactly once and on no account at all.</p>
 *
 * <p>It was the email. The account form sends an empty box as an empty string, and
 * {@code emailId} is unique: MySQL allows any number of NULLs in a unique column but
 * only one empty string, so the first account saved without an address took it and
 * every one after it collided. Most of the register has no email, so this was every
 * second account.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountCreationTest {

    @Autowired UserService userService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired SewadarRoleRepository sewadarRoleRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone zone;
    private SewadarRole coordinator;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("AC").name("Accounts").active(true).build());
        coordinator = sewadarRoleRepository.findByNameIgnoreCase("Co-ordinator").orElseGet(() ->
                sewadarRoleRepository.save(
                        SewadarRole.builder().name("Co-ordinator").active(true).build()));
        signInAsAdmin();
    }

    @Test
    @DisplayName("two accounts with no email address can both be created")
    void blankEmailIsNoEmail() {
        Sewadar first = sewadar("L04678", "Asha Rawal");
        Sewadar second = sewadar("L04679", "Bina Devi");

        userService.create(request("acct-one", first, ""));
        // The second one is where it used to fail, on the empty string the first took.
        userService.create(request("acct-two", second, ""));

        assertThat(userRepository.findByUsernameIgnoreCase("acct-one")).isPresent();
        assertThat(userRepository.findByUsernameIgnoreCase("acct-two")).isPresent();
    }

    @Test
    @DisplayName("a blank email is stored as nothing, not as an empty string")
    void blankEmailIsStoredAsNull() {
        userService.create(request("acct-blank", sewadar("L04680", "Chitra Bai"), "   "));

        assertThat(userRepository.findByUsernameIgnoreCase("acct-blank"))
                .get()
                .extracting(User::getEmail)
                .isNull();
    }

    @Test
    @DisplayName("a real email address still has to be unique")
    void realEmailIsStillUnique() {
        userService.create(request("acct-mail", sewadar("L04681", "Devi Kumari"), "office@example.com"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        userService.create(request("acct-mail-2", sewadar("L04682", "Esha Singh"),
                                "office@example.com")))
                .hasMessageContaining("already");
    }

    @Test
    @DisplayName("the account points at its sewadar's photo rather than carrying a copy")
    void theAccountPointsAtTheSewadarsPhoto() {
        Sewadar person = sewadar("L04683", "Farida Khan");
        userService.create(request("acct-photo", person, ""));

        UserResponse row = row("acct-photo");

        // The pointer, not the bytes: the account has no photo of its own and the
        // screen reads the sewadar's by id.
        assertThat(row.hasPhoto()).isFalse();
        assertThat(row.sewadarId()).isEqualTo(person.getId());
    }

    @Test
    @DisplayName("an older account finds its sewadar by GR. No when nothing links them")
    void anAccountWithOnlyAGrNumberStillFindsItsSewadar() {
        /*
         * Every account on the live register was made before the link existed: they
         * carry the GR. No the office typed and no `sewadars.user_id` pointing back.
         * Matching on the number is what makes the photo appear for them rather than
         * only for accounts made from here on.
         */
        Sewadar person = sewadar("L04690", "Gita Sharma");
        userRepository.save(User.builder()
                .username("acct-legacy")
                .passwordHash(passwordEncoder.encode("Office@12345"))
                .fullName(person.getName())
                .badgeNo(person.getBadgeNumber())
                .role(Role.SEWADAR)
                .enabled(true)
                .build());

        assertThat(row("acct-legacy").sewadarId()).isEqualTo(person.getId());
    }

    @Test
    @DisplayName("a GR. No that is on no sewadar simply has no photo to show")
    void aGrNumberThatMatchesNobodyIsNotAnError() {
        userRepository.save(User.builder()
                .username("acct-unknown")
                .passwordHash(passwordEncoder.encode("Office@12345"))
                .fullName("Nobody Here")
                .badgeNo("L99999")
                .role(Role.SEWADAR)
                .enabled(true)
                .build());

        assertThat(row("acct-unknown").sewadarId()).isNull();
    }

    private UserResponse row(String username) {
        return userService.search(null, null, null, null,
                        PageRequest.of(0, 25, Sort.by("username"))).content().stream()
                .filter(u -> username.equals(u.username()))
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------------------ helpers

    private CreateUserRequest request(String username, Sewadar linked, String email) {
        return new CreateUserRequest(username, "Office@12345", linked.getName(), email,
                linked.getMobile(), coordinator.getId(), linked.getBadgeNumber(), Gender.FEMALE,
                Role.COORDINATOR, Set.of(zone.getId()), linked.getId(), false);
    }

    private Sewadar sewadar(String badge, String name) {
        return sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge).name(name).zone(zone).gender(Gender.FEMALE)
                .mobile("98260000" + badge.substring(badge.length() - 2)).build());
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("accounts-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Accounts Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

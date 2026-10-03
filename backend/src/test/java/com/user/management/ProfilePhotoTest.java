package com.user.management;

import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.LoginResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.AuthService;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The face beside the name, once somebody is signed in.
 *
 * <p>Reported as *"I login successfully but my profile image is not shown"*. The
 * shell was asking for the account's own photo, and almost no account has one: the
 * office uploads a picture to a <b>sewadar</b> record, taken for the badge, and
 * never to a login. So the header drew initials for everybody.</p>
 *
 * <p>The profile now carries where to read the picture from. An account with its own
 * photo keeps it; otherwise it borrows the sewadar's, found by the account link or -
 * for the accounts made before that link existed, which is all of the live ones - by
 * the GR. No on the account. It is a pointer: no bytes are copied, and changing the
 * photo on the sewadar record changes what the header draws.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfilePhotoTest {

    @Autowired AuthService authService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone zone;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("PP").name("Profile").active(true).build());
    }

    @Test
    @DisplayName("an account with no photo borrows the one on its linked sewadar")
    void borrowsTheLinkedSewadarsPhoto() {
        User account = account("photo-linked", null);
        Sewadar person = sewadar("P-001", account);

        LoginResponse me = signedInProfile(account);

        assertThat(me.photoUpdatedAt()).isNull();
        assertThat(me.photoSewadarId()).isEqualTo(person.getId());
        assertThat(me.sewadarPhotoUpdatedAt()).isEqualTo(person.getPhotoUpdatedAt());
    }

    @Test
    @DisplayName("an older account finds its sewadar by GR. No, with nothing linking them")
    void findsTheSewadarByGrNumber() {
        // Every account on the live server is this one: a GR. No and no back-pointer.
        Sewadar person = sewadar("P-002", null);
        User account = account("photo-legacy", person.getBadgeNumber());

        assertThat(signedInProfile(account).photoSewadarId()).isEqualTo(person.getId());
    }

    @Test
    @DisplayName("an account with its own photo keeps it")
    void itsOwnPhotoWins() {
        User account = account("photo-own", null);
        account.setPhotoUpdatedAt(Instant.now());
        userRepository.save(account);
        sewadar("P-003", account);

        LoginResponse me = signedInProfile(account);

        assertThat(me.photoUpdatedAt()).isNotNull();
        // The sewadar is still named, so removing the account's photo falls back
        // without another round trip.
        assertThat(me.photoSewadarId()).isNotNull();
    }

    @Test
    @DisplayName("an account that matches nobody has no photo to borrow")
    void nobodyToBorrowFrom() {
        LoginResponse me = signedInProfile(account("photo-none", "P-999"));

        assertThat(me.photoUpdatedAt()).isNull();
        assertThat(me.photoSewadarId()).isNull();
        assertThat(me.sewadarPhotoUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("borrowing a photo does not narrow what the account may see")
    void theBorrowedPhotoIsNotADataScope() {
        /*
         * `sewadarId` on the profile means "this login sees only this record" and is
         * set for a Sewadar login alone. The photo pointer is a separate field for
         * exactly this reason: an Office Incharge borrowing a face must not have
         * their screens narrowed to the one row it came from.
         */
        User incharge = account("photo-office", "P-004");
        sewadar("P-004", null);

        LoginResponse me = signedInProfile(incharge);

        assertThat(me.photoSewadarId()).isNotNull();
        assertThat(me.sewadarId()).isNull();
    }

    // ------------------------------------------------------------------ helpers

    /** Signs the account in and reads back the profile the shell would get. */
    private LoginResponse signedInProfile(User account) {
        AppUserPrincipal principal = new AppUserPrincipal(account, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return authService.me();
    }

    private User account(String username, String badgeNo) {
        return userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("Office@12345"))
                .fullName("Profile " + username)
                .badgeNo(badgeNo)
                .role(Role.OFFICE_USER)
                .enabled(true)
                .build());
    }

    private Sewadar sewadar(String badge, User linkedTo) {
        return sewadarRepository.save(Sewadar.builder()
                .badgeNumber(badge)
                .name("Sewadar " + badge)
                .zone(zone)
                .gender(Gender.FEMALE)
                .user(linkedTo)
                .photoUpdatedAt(Instant.now())
                .build());
    }
}

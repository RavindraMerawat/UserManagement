package com.user.management;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.CheckInOutRequest;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.AppUserPrincipal;
import com.user.management.service.CheckInOutService;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The office is in India, so "now" is India Standard Time.
 *
 * <p>Attendance is stored as a day and a clock reading with no zone on them, taken
 * from {@code LocalTime.now()} - which reads the JVM's default zone. The server keeps
 * its clock in UTC, so a check in at 4:51 pm was written down as 11:21 and shown that
 * way everywhere afterwards. These tests hold the application to IST whatever the
 * machine under it is set to.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IndiaTimeTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Autowired CheckInOutService checkInOutService;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Sewadar sewadar;

    @BeforeEach
    void seed() {
        Zone zone = zoneRepository.save(Zone.builder().code("TZ").name("Time").active(true).build());
        sewadar = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("T-001").name("Timed Sewadar").zone(zone).build());
        signInAsAdmin();
    }

    @Test
    @DisplayName("the application's clock is India Standard Time")
    void defaultZoneIsIndia() {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(INDIA);
    }

    @Test
    @DisplayName("a check in is stamped with the time in India, not the server's UTC")
    void checkInIsStampedInIndia() {
        ZonedDateTime indiaNow = ZonedDateTime.now(INDIA);

        AttendanceResponse marked = checkInOutService.checkIn(
                new CheckInOutRequest(sewadar.getId(), null, null, null, null));

        assertThat(marked.attendanceDate()).isEqualTo(indiaNow.toLocalDate());
        /*
         * Within a minute of the Indian wall clock. Comparing to UTC instead would
         * be out by 5h30m, which is the whole of this test: a tolerance that loose
         * would pass either way.
         */
        assertThat(Duration.between(indiaNow.toLocalTime(), marked.inTime()).abs())
                .isLessThan(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("today is India's today")
    void todayIsIndiasToday() {
        assertThat(LocalDate.now()).isEqualTo(LocalDate.now(INDIA));
        assertThat(Duration.between(LocalTime.now(INDIA), LocalTime.now()).abs())
                .isLessThan(Duration.ofMinutes(1));
    }

    private void signInAsAdmin() {
        User admin = userRepository.save(User.builder()
                .username("tz-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Test@12345"))
                .fullName("Time Admin")
                .role(Role.ADMIN)
                .enabled(true)
                .build());
        AppUserPrincipal principal = new AppUserPrincipal(admin, null, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

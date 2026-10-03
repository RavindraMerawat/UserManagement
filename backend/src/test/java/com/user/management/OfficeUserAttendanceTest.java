package com.user.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * An Office User marking attendance.
 *
 * <p>Reported as "office user unable to mark attendance". The login said they could -
 * {@code Capabilities} grants every Office User {@code markAttendance}, so the screen
 * drew the Check in button - and the URL rule in {@code SecurityConfig} did not list
 * OFFICE_USER, so pressing it returned 403. The two halves of the same decision
 * disagreed, and the half the office met was the one that said no.</p>
 *
 * <p>These tests run through the whole chain - sign in, bearer token, filter, URL
 * rule, controller, service - because the fault was in the chain and not in any one
 * of its links. The last case is the one that stops the fix going too far: a login
 * whose designation does not grant marking is still refused, now by the rule that
 * knows about designations rather than by the one that does not.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OfficeUserAttendanceTest {

    private static final String PASSWORD = "Office@12345";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SewadarRepository sewadarRepository;
    @Autowired SewadarRoleRepository sewadarRoleRepository;
    @Autowired ZoneRepository zoneRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Zone zone;
    private Sewadar toMark;

    @BeforeEach
    void seed() {
        zone = zoneRepository.save(Zone.builder().code("OU").name("Office").active(true).build());
        toMark = sewadarRepository.save(Sewadar.builder()
                .badgeNumber("A-100").name("Anita Marked").zone(zone).build());
    }

    @Test
    @DisplayName("an Office User with the Office Sewadar designation can check somebody in")
    void officeSewadarCanMark() throws Exception {
        String token = signIn(account("office-desig", Role.OFFICE_USER, "Office Sewadar"));

        checkIn(token).andExpect(result ->
                assertThat(result.getResponse().getStatus())
                        .as("check in as an Office Sewadar")
                        .isEqualTo(200));
    }

    @Test
    @DisplayName("an Office User with no designation can check somebody in too")
    void plainOfficeUserCanMark() throws Exception {
        // No sewadar linked, so the grant comes from the account role. That grant
        // allows marking - the desk is where the office marks people in.
        String token = signIn(account("office-plain", Role.OFFICE_USER, null));

        checkIn(token).andExpect(result ->
                assertThat(result.getResponse().getStatus())
                        .as("check in as a plain Office User")
                        .isEqualTo(200));
    }

    @Test
    @DisplayName("a designation that does not grant marking is still refused")
    void guideSewadarIsStillRefused() throws Exception {
        String token = signIn(account("office-guide", Role.OFFICE_USER, "Guide Sewadar"));

        checkIn(token).andExpect(result ->
                assertThat(result.getResponse().getStatus())
                        .as("Guide Sewadar is an ordinary sewadar in the matrix")
                        .isEqualTo(403));
    }

    @Test
    @DisplayName("a Sewadar login cannot mark anyone, which is what the URL rule is for")
    void sewadarLoginIsRefused() throws Exception {
        String token = signIn(account("sewadar-login", Role.SEWADAR, null));

        checkIn(token).andExpect(result ->
                assertThat(result.getResponse().getStatus())
                        .as("a Sewadar login marking attendance")
                        .isEqualTo(403));
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions checkIn(String token) throws Exception {
        return mvc.perform(post("/api/attendance/check-in")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sewadarId", toMark.getId()))));
    }

    /** An account, with the designation the permission rules read. */
    private String account(String username, Role role, String designation) {
        SewadarRole held = designation == null ? null
                : sewadarRoleRepository.findByNameIgnoreCase(designation)
                        .orElseGet(() -> sewadarRoleRepository.save(
                                SewadarRole.builder().name(designation).active(true).build()));
        userRepository.save(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName(username)
                .role(role)
                .sewadarRole(held)
                .enabled(true)
                .build());
        return username;
    }

    /** Signs in the way the screen does, so the token carries whatever login decides. */
    private String signIn(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("username", username, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }
}

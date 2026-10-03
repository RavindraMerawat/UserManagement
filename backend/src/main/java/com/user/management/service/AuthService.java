package com.user.management.service;

import com.user.management.entity.Role;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.ChangePasswordRequest;
import com.user.management.model.LoginRequest;
import com.user.management.model.LoginResponse;
import com.user.management.entity.Sewadar;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.security.AppUserDetailsService;
import com.user.management.security.AppUserPrincipal;
import com.user.management.security.Capabilities;
import com.user.management.security.CurrentUserService;
import com.user.management.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final SewadarRepository sewadarRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUser;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> NotFoundException.of("User", principal.getUserId()));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(principal);
        log.info("{} signed in as {}", principal.getUsername(), principal.getRole());

        Capabilities.Grant capabilities =
                Capabilities.of(principal.getRole(), principal.getDesignation());

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.expiryOf(token),
                principal.getUserId(),
                principal.getUsername(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getRole().name(),
                principal.getRole().getDisplayName(),
                principal.getZoneIds(),
                user.getZones().stream().map(Zone::getName).toList(),
                principal.getSewadarId(),
                principal.isMustChangePassword(),
                user.getPhotoUpdatedAt() != null,
                user.getPhotoUpdatedAt(),
                photoSewadar(user).map(Sewadar::getId).orElse(null),
                photoSewadar(user).map(Sewadar::getPhotoUpdatedAt).orElse(null),
                principal.getDesignation(),
                capabilities.manageSewadars(),
                capabilities.manageBadges(),
                capabilities.manageConstruction(),
                capabilities.markAttendance(),
                capabilities.manageAttendanceRecords(),
                capabilities.viewMonthlyReport(),
                capabilities.fullAttendance(),
                user.getGender(),
                capabilities.createZoneRequest(),
                capabilities.reviewZoneRequest(),
                capabilities.administer(),
                Capabilities.menu(capabilities));
    }

    /**
     * The sewadar whose photo stands in for an account that has none of its own.
     *
     * <p>Nobody uploads a picture to a login. The photo of a person is on their
     * sewadar record, taken for their badge, so that is the one the shell draws -
     * by id, not by copying the bytes.</p>
     *
     * <p>Two ways of finding them, because the link proper ({@code sewadars.user_id})
     * is set when an account is made from a GR. No and every account made before
     * that has none. The GR. No on the account identifies the same person, so it is
     * the second way. An account with neither simply shows its initials.</p>
     */
    private Optional<Sewadar> photoSewadar(User user) {
        Optional<Sewadar> linked = sewadarRepository.findByUserId(user.getId());
        if (linked.isPresent() || !StringUtils.hasText(user.getBadgeNo())) {
            return linked;
        }
        return sewadarRepository.findByBadgeNumberIgnoreCase(user.getBadgeNo().trim());
    }

    /** Re-reads the signed-in account, used by the UI on page refresh. */
    @Transactional(readOnly = true)
    public LoginResponse me() {
        AppUserPrincipal principal = currentUser.principal();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> NotFoundException.of("User", principal.getUserId()));
        Capabilities.Grant capabilities =
                Capabilities.of(principal.getRole(), principal.getDesignation());

        return new LoginResponse(
                null,
                "Bearer",
                null,
                principal.getUserId(),
                principal.getUsername(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getRole().name(),
                principal.getRole().getDisplayName(),
                principal.getZoneIds(),
                user.getZones().stream().map(Zone::getName).toList(),
                principal.getSewadarId(),
                principal.isMustChangePassword(),
                user.getPhotoUpdatedAt() != null,
                user.getPhotoUpdatedAt(),
                photoSewadar(user).map(Sewadar::getId).orElse(null),
                photoSewadar(user).map(Sewadar::getPhotoUpdatedAt).orElse(null),
                principal.getDesignation(),
                capabilities.manageSewadars(),
                capabilities.manageBadges(),
                capabilities.manageConstruction(),
                capabilities.markAttendance(),
                capabilities.manageAttendanceRecords(),
                capabilities.viewMonthlyReport(),
                capabilities.fullAttendance(),
                user.getGender(),
                capabilities.createZoneRequest(),
                capabilities.reviewZoneRequest(),
                capabilities.administer(),
                Capabilities.menu(capabilities));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        AppUserPrincipal principal = currentUser.principal();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> NotFoundException.of("User", principal.getUserId()));

        // A BadCredentialsException here would be reported with the deliberately vague
        // login message, which is unhelpful when the user is changing their own password.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Your current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("The new password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("{} changed their password", user.getUsername());
    }

    /**
     * Left-hand navigation for a role. Home, About and Contact are open to everyone;
     * the data screens follow the role.
     */
    public List<String> menuFor(Role role) {
        return switch (role) {
            // Listed in the order the sidebar draws them, so the two stay in step.
            case ADMIN -> List.of("HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT", "REQUEST",
                    "USERS", "SETUP", "CONTACT");
            case OFFICE_ADMIN -> List.of("HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT", "REQUEST",
                    "USERS", "SETUP", "CONTACT");
            case COORDINATOR, ZONE_INCHARGE, SUPERVISOR -> List.of("HOME", "SEWADAR", "ATTENDANCE",
                    "BADGES", "REPORT", "REQUEST", "CONTACT");
            case OFFICE_USER -> List.of("HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT",
                    "REQUEST", "CONTACT");
            case SEWADAR -> List.of("HOME", "ATTENDANCE", "BADGES", "REPORT", "REQUEST", "CONTACT");
        };
    }
}

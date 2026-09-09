package com.user.management.service;

import com.user.management.entity.Role;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.ChangePasswordRequest;
import com.user.management.model.LoginRequest;
import com.user.management.model.LoginResponse;
import com.user.management.repository.UserRepository;
import com.user.management.security.AppUserDetailsService;
import com.user.management.security.AppUserPrincipal;
import com.user.management.security.CurrentUserService;
import com.user.management.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final UserRepository userRepository;
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
                menuFor(principal.getRole()));
    }

    /** Re-reads the signed-in account, used by the UI on page refresh. */
    @Transactional(readOnly = true)
    public LoginResponse me() {
        AppUserPrincipal principal = currentUser.principal();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> NotFoundException.of("User", principal.getUserId()));
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
                menuFor(principal.getRole()));
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
            case ADMIN -> List.of("HOME", "ABOUT", "SEWADAR", "ATTENDANCE", "REPORT", "REQUEST", "USERS",
                    "ZONES", "CONTACT");
            case OFFICE_ADMIN -> List.of("HOME", "ABOUT", "SEWADAR", "ATTENDANCE", "REPORT", "REQUEST",
                    "ZONES", "CONTACT");
            case COORDINATOR, ZONE_INCHARGE, SUPERVISOR -> List.of("HOME", "ABOUT", "SEWADAR",
                    "ATTENDANCE", "REPORT", "REQUEST", "CONTACT");
            case OFFICE_USER -> List.of("HOME", "ABOUT", "SEWADAR", "ATTENDANCE", "REPORT", "REQUEST",
                    "CONTACT");
            case SEWADAR -> List.of("HOME", "ABOUT", "ATTENDANCE", "REPORT", "REQUEST", "CONTACT");
        };
    }
}

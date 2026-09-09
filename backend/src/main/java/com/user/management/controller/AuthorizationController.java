package com.user.management.controller;

import com.user.management.model.ChangePasswordRequest;
import com.user.management.model.DashboardResponse;
import com.user.management.model.LoginRequest;
import com.user.management.model.LoginResponse;
import com.user.management.service.AuthService;
import com.user.management.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "1. Authorization", description = """
        Sign in, read the signed-in profile, load the role scoped dashboard and change
        your own password. The login response carries the role, the accessible zones and
        the left hand menu that role is allowed to open.
        """)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthorizationController {

    private final AuthService authService;
    private final DashboardService dashboardService;

    @Operation(summary = "Sign in and receive a JWT",
            description = "Returns the token, the role, the accessible zones and the left menu for that role.")
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Profile of the signed-in user")
    @GetMapping("/me")
    public LoginResponse me() {
        return authService.me();
    }

    @Operation(summary = "Home screen figures for the signed-in user")
    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.load();
    }

    @Operation(summary = "Change your own password")
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}

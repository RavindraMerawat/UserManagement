package com.user.management.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        Long userId,
        String username,
        String fullName,
        String email,
        String role,
        String roleDisplayName,
        Set<Long> zoneIds,
        List<String> zoneNames,
        Long sewadarId,
        boolean mustChangePassword,
        List<String> menu
) {
}

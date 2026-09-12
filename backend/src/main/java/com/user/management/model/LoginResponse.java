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
        /*
         * The signed-in account's own photo, so the shell can draw it beside the name
         * without a second call. `photoUpdatedAt` is the cache key the client uses -
         * a new upload changes it, which is what makes the new image appear.
         */
        boolean hasPhoto,
        Instant photoUpdatedAt,
        List<String> menu
) {
}

package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Schema(description = "The signed-in account: who they are, what they may reach, "
        + "and - on login - the token to send back.")
public record LoginResponse(

        @Schema(description = "JWT for the Authorization header. Null on /api/auth/me.")
        String token,

        @Schema(example = "Bearer") String tokenType,

        @Schema(description = "When the token stops working. Null on /api/auth/me.")
        Instant expiresAt,

        Long userId,
        String username,
        String fullName,
        String email,

        @Schema(example = "ZONE_INCHARGE") String role,
        @Schema(example = "Zone Incharge") String roleDisplayName,

        @Schema(description = "Zones this account may read and write. Empty means every zone.")
        Set<Long> zoneIds,

        List<String> zoneNames,

        @Schema(description = "Set when the account belongs to a sewadar, so the app can "
                + "narrow their screens to their own record.")
        Long sewadarId,

        @Schema(description = "A new account must set its own password before going further.")
        boolean mustChangePassword,
        /*
         * The signed-in account's own photo, so the shell can draw it beside the name
         * without a second call. `photoUpdatedAt` is the cache key the client uses -
         * a new upload changes it, which is what makes the new image appear.
         */
        @Schema(description = "Whether this account has a photo at GET /api/users/{userId}/photo.")
        boolean hasPhoto,

        @Schema(description = "When that photo was last uploaded, or null when there is none. "
                + "Clients key their image cache on it, so a new upload replaces the old picture.")
        Instant photoUpdatedAt,

        @Schema(description = "Screens this role may open, mirrored by the client's route guards.",
                example = "[\"HOME\",\"SEWADAR\",\"ATTENDANCE\"]")
        List<String> menu
) {
}

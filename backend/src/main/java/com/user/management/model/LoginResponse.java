package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;

import com.user.management.entity.Gender;

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
        /*
         * The designation the permission rules were read from, and the answers they
         * gave. The browser gets the answers rather than the rules: one matrix, on
         * the server, is the only way the menu and the API can agree about who may
         * do what.
         */
        String designation,
        boolean canManageSewadars,
        boolean canManageBadges,
        boolean canManageConstruction,
        boolean canMarkAttendance,
        /** Open All Attendance Record, and correct what is on it. */
        boolean canManageAttendanceRecords,
        /** Open the Monthly Report. */
        boolean canViewMonthlyReport,
        /** Open Zone Attendance and Manage Past Attendance, not only Mark Attendance. */
        boolean canUseFullAttendance,
        /**
         * Whose register this account reads, or null for both.
         *
         * <p>The screens need it as well as the server: a female account should not
         * be shown a card counting men, even one reading zero. The server still
         * narrows the data; this only stops the screen asking.</p>
         */
        Gender gender,
        boolean canCreateZoneRequest,
        boolean canReviewRequests,
        boolean canAdminister,
        List<String> menu
) {
}

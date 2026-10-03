package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import com.user.management.entity.User;
import com.user.management.entity.Zone;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String mobile,
        Role role,
        String roleDisplayName,
        /** The role from the roles table - shown on the screens as Designation. */
        Long roleId,
        String designationName,
        /** The GR. No this login belongs to. */
        String badgeNumber,
        /** Whose register this account reads. Null until somebody sets it. */
        Gender gender,
        Set<Long> zoneIds,
        List<String> zoneNames,
        boolean enabled,
        boolean mustChangePassword,
        Instant lastLoginAt,
        boolean hasPhoto,
        Instant photoUpdatedAt,
        /**
         * The sewadar this account belongs to, and when that record's photo last
         * changed.
         *
         * <p>An account with no photo of its own shows the sewadar's. The bytes are
         * not copied: the photo lives once, on the sewadar, and this is the pointer
         * to it - change the sewadar's picture and the account's changes with it.</p>
         */
        Long sewadarId,
        Instant sewadarPhotoUpdatedAt
) {
    public static UserResponse from(User u) {
        return from(u, null);
    }

    /** @param linked the sewadar this account belongs to, or null if it has none */
    public static UserResponse from(User u, com.user.management.entity.Sewadar linked) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                u.getMobile(),
                u.getRole(),
                u.getRole().getDisplayName(),
                u.getSewadarRole() == null ? null : u.getSewadarRole().getId(),
                u.getSewadarRole() == null ? null : u.getSewadarRole().getName(),
                u.getBadgeNo(),
                u.getGender(),
                u.getZones().stream().map(Zone::getId).collect(Collectors.toSet()),
                u.getZones().stream().map(Zone::getName).toList(),
                u.isEnabled(),
                u.isMustChangePassword(),
                u.getLastLoginAt(),
                u.getPhotoUpdatedAt() != null,
                u.getPhotoUpdatedAt(),
                linked == null ? null : linked.getId(),
                linked == null ? null : linked.getPhotoUpdatedAt());
    }
}

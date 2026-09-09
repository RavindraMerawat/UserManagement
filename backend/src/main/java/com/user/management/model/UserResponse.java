package com.user.management.model;

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
        Set<Long> zoneIds,
        List<String> zoneNames,
        boolean enabled,
        boolean mustChangePassword,
        Instant lastLoginAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                u.getMobile(),
                u.getRole(),
                u.getRole().getDisplayName(),
                u.getZones().stream().map(Zone::getId).collect(Collectors.toSet()),
                u.getZones().stream().map(Zone::getName).toList(),
                u.isEnabled(),
                u.isMustChangePassword(),
                u.getLastLoginAt());
    }
}

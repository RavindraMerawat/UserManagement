package com.user.management.model;

import com.user.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String mobile,
        Role role,
        Set<Long> zoneIds,
        Boolean enabled,
        /** Sets a new password when present. */
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters") String newPassword
) {
}

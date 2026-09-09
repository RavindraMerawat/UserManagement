package com.user.management.model;

import com.user.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 60) String username,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        String password,
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String mobile,
        @NotNull(message = "Role is required") Role role,
        /** Required for ZONE_INCHARGE and SUPERVISOR. */
        Set<Long> zoneIds,
        /** Required when role is SEWADAR: the sewadar record to link. */
        Long sewadarId,
        Boolean mustChangePassword
) {
}

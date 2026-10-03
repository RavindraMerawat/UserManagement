package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        /**
         * A new username, or left out to keep the one it has.
         *
         * <p>Renaming an account ends any session signed in as the old name: the
         * token carries the username, and the server will not know the new one.
         * The screen says so where the field is.</p>
         */
        @Size(min = 3, max = 60) String username,
        @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be 10 digits")
        @Size(max = 20) String mobile,
        Long roleId,

        /** The GR. No this login belongs to. */
        @Size(max = 40) String badgeNumber,

        /** Whose register this account reads. */
        Gender gender,

        Role role,
        Set<Long> zoneIds,
        Boolean enabled,
        /** Sets a new password when present. */
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters") String newPassword
) {
}

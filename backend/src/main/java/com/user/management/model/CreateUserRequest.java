package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 60) String username,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        String password,
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be 10 digits")
        @Size(max = 20) String mobile,
        /**
         * The role from the roles table - the screens call it Designation. This is
         * what the form sends and what the permission rules read.
         */
        @NotNull(message = "Role is required") Long roleId,

        /** The GR. No this login belongs to, as the office writes it. */
        @Size(max = 40) String badgeNumber,

        /**
         * Whose register this account reads: MALE sees the male sewadars, FEMALE the
         * female ones. Left out, the account sees both, which is how every account
         * made before this field behaved.
         */
        Gender gender,

        /**
         * The coarse account type Spring Security matches on. Derived from
         * {@code roleId} when it is not sent, which is the normal case.
         */
        Role role,
        /** Required for ZONE_INCHARGE and SUPERVISOR. */
        Set<Long> zoneIds,
        /** Required when role is SEWADAR: the sewadar record to link. */
        Long sewadarId,
        Boolean mustChangePassword
) {
}

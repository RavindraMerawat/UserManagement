package com.user.management.model;

import jakarta.validation.constraints.Size;

/**
 * Add or rename a role.
 *
 * <p>Accepts either field name so the screen can keep calling it "designation"
 * while the table calls it a role; {@link #text()} returns whichever was sent.</p>
 */
public record SewadarRoleRequest(
        @Size(max = 80) String name,
        @Size(max = 80) String designation,
        Boolean active
) {

    public String text() {
        return name != null && !name.isBlank() ? name : designation;
    }
}

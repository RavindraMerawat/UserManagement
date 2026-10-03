package com.user.management.model;

import com.user.management.entity.SewadarRole;

/**
 * One role, for the Setup list and the sewadar form's dropdown.
 *
 * <p>Both names are sent: {@code name} is what the table holds, and
 * {@code designation} is the same string under the word the screens use, so the
 * browser never has to know the two are the same thing.</p>
 */
public record SewadarRoleResponse(Long id, String name, String designation, boolean active) {

    public static SewadarRoleResponse from(SewadarRole role) {
        return new SewadarRoleResponse(role.getId(), role.getName(), role.getName(),
                role.isActive());
    }
}

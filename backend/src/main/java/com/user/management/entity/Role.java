package com.user.management.entity;

import java.util.Set;

/**
 * Application roles. Spring Security authorities are stored as ROLE_&lt;name&gt;.
 */
public enum Role {

    /** Full access to every module and every zone. */
    ADMIN("Admin"),
    /** Same data reach as ADMIN, cannot manage user accounts or system settings. */
    OFFICE_ADMIN("Office Admin"),
    /** Access limited to the zones the user co-ordinates. */
    COORDINATOR("Co-ordinator"),
    /** Access limited to the zones the user is in charge of. */
    ZONE_INCHARGE("Zone Incharge"),
    /** Access limited to the zones the user supervises. */
    SUPERVISOR("Supervisor"),
    /** Read only across all zones, used by office/back-office staff. */
    OFFICE_USER("Office User"),
    /** Access limited to the sewadar's own records. */
    SEWADAR("Sewadar");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    /** Roles that see data for every zone. */
    public static final Set<Role> GLOBAL_SCOPE = Set.of(ADMIN, OFFICE_ADMIN, OFFICE_USER);

    /** Roles whose data reach is limited to their assigned zones. */
    public static final Set<Role> ZONE_SCOPE = Set.of(COORDINATOR, ZONE_INCHARGE, SUPERVISOR);

    public boolean isGlobalScope() {
        return GLOBAL_SCOPE.contains(this);
    }

    public boolean isZoneScope() {
        return ZONE_SCOPE.contains(this);
    }
}

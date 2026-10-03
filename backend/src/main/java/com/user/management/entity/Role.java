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

    /**
     * The account type a designation from the roles table maps to.
     *
     * <p>Five of the designations line up with an account type the security rules
     * already know. Everything else on the list - Group Incharge, Gate Incharge and
     * the rest - is an ordinary sewadar as far as this coarse check goes, which is
     * exactly what the permission matrix grants them anyway.</p>
     *
     * <p>This lives here rather than in the service because the screens need the
     * same answer - which account type a chosen designation implies decides whether
     * the form asks for zones - and two copies of it would drift.</p>
     */
    public static Role forDesignation(String designationName) {
        if (designationName == null) {
            return SEWADAR;
        }
        // Letters alone: "Co-ordinator" and "Coordinator" are the same designation,
        // and a hyphen must not decide what an account may do.
        String key = designationName.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return switch (key) {
            case "officeincharge" -> OFFICE_ADMIN;
            case "officesewadar" -> OFFICE_USER;
            case "coordinator" -> COORDINATOR;
            case "zoneincharge" -> ZONE_INCHARGE;
            case "supervisor" -> SUPERVISOR;
            default -> SEWADAR;
        };
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
    
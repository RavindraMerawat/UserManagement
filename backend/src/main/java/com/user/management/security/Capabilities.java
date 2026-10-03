package com.user.management.security;

import com.user.management.entity.Role;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a person may see and do, decided by their <b>designation</b>.
 *
 * <p>A login's designation is the designation on the sewadar record linked to it.
 * The whole matrix is in one place here, spelled out, because a permission rule
 * scattered across controllers is a permission rule nobody can check.</p>
 *
 * <h3>Two deliberate exceptions to "designation decides"</h3>
 * <ol>
 *   <li><b>ADMIN is an override.</b> The admin login has no sewadar record, so it
 *       has no designation, and approving a zone change is an Admin-only right in
 *       any case. Without this the office could lock itself out by editing the
 *       designation list.</li>
 *   <li><b>No designation falls back to the account's Role.</b> Every existing
 *       login predates this field and has none yet. Without the fallback, turning
 *       this on would remove everyone's access until each record was edited. Once
 *       designations are filled in, the fallback stops being reached.</li>
 * </ol>
 *
 * <p>Designations not listed below - Group Incharge, Block Incharge, Gate Incharge
 * and the rest - are treated as an ordinary sewadar: they see their own record and
 * nothing else. That was not specified, and showing too little is the safer way to
 * be wrong.</p>
 */
public final class Capabilities {

    /** One row of the matrix. */
    public record Grant(
            /** Screens the menu shows. */
            Set<String> screens,
            /** Add, edit and delete sewadar records. */
            boolean manageSewadars,
            /** Issue and collect badges. */
            boolean manageBadges,
            /** Record and update a sewadar's construction sewa count. */
            boolean manageConstruction,
            /** Mark and correct attendance. */
            boolean markAttendance,
            /**
             * Open All Attendance Record, and change or remove an entry on it.
             *
             * <p>Separate from {@link #markAttendance()} on purpose: marking is the
             * day's work and most of the office does it, while going back over what
             * was marked and altering it is the office's own correction, kept with
             * the Admin and the Office Incharge.</p>
             */
            boolean manageAttendanceRecords,
            /** Open the Monthly Report. The sewadar's own hours report is separate. */
            boolean viewMonthlyReport,
            /**
             * The whole Attendance module - Zone Attendance and Manage Past
             * Attendance beside Mark Attendance.
             *
             * <p>Everyone who marks sees Mark Attendance. Marking a zone sheet at
             * once, and entering a day that has already gone, are the office's own
             * work and sit behind this.</p>
             */
            boolean fullAttendance,
            /** Raise a zone change request. */
            boolean createZoneRequest,
            /** Approve or reject one. Admin only. */
            boolean reviewZoneRequest,
            /** Administer login accounts and the Setup lists. */
            boolean administer,
            /** Data reach is limited to the zones on the account. */
            boolean zoneScoped
    ) {
    }

    private static final Set<String> OPERATIONAL = Set.of(
            "HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT", "REQUEST", "CONTACT");

    private static final Set<String> EVERYTHING = Set.of(
            "HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT", "REQUEST", "CONTACT",
            "SETUP", "USERS", "ZONES");

    private static final Set<String> OWN_RECORD_ONLY = Set.of(
            "HOME", "ATTENDANCE", "BADGES", "REPORT", "REQUEST", "CONTACT");

    /** Everything, and the only one that may approve a zone change. */
    private static final Grant ADMIN = new Grant(
            EVERYTHING,
            true,   // manageSewadars
            true,   // manageBadges
            true,   // manageConstruction
            true,   // markAttendance
            true,   // manageAttendanceRecords
            true,   // viewMonthlyReport
            true,   // fullAttendance
            true,   // createZoneRequest
            true,   // reviewZoneRequest
            true,   // administer
            false); // zoneScoped

    /**
     * The zone trio. They read their zones and raise requests; they do not add
     * sewadars and they do not issue badges - that is office work.
     */
    private static final Grant COORDINATOR = new Grant(
            OPERATIONAL,
            false,  // manageSewadars
            false,  // manageBadges
            false,  // manageConstruction - they read the count, they do not set it
            true,   // markAttendance
            false,  // manageAttendanceRecords - they mark, they do not go back and alter
            false,  // viewMonthlyReport
            false,  // fullAttendance - Mark Attendance, and that is the module for them
            true,   // createZoneRequest
            false,  // reviewZoneRequest
            false,  // administer
            true);  // zoneScoped

    /** As above, but a supervisor does not raise zone change requests. */
    private static final Grant SUPERVISOR = new Grant(
            OPERATIONAL,
            false,  // manageSewadars
            false,  // manageBadges
            false,  // manageConstruction
            true,   // markAttendance
            false,  // manageAttendanceRecords
            false,  // viewMonthlyReport
            false,  // fullAttendance
            false,  // createZoneRequest
            false,  // reviewZoneRequest
            false,  // administer
            true);  // zoneScoped

    /** Office, senior: everything the office does, including the Setup lists. */
    private static final Grant OFFICE_INCHARGE = new Grant(
            EVERYTHING,
            true,   // manageSewadars
            true,   // manageBadges
            true,   // manageConstruction
            true,   // markAttendance
            true,   // manageAttendanceRecords
            true,   // viewMonthlyReport
            true,   // fullAttendance
            true,   // createZoneRequest
            false,  // reviewZoneRequest
            true,   // administer
            false); // zoneScoped

    /**
     * Office, and the same day-to-day work, but not account administration.
     * Creating logins is left with the Office Incharge and the Admin.
     */
    private static final Grant OFFICE_SEWADAR = new Grant(
            OPERATIONAL,
            true,   // manageSewadars
            true,   // manageBadges
            true,   // manageConstruction
            true,   // markAttendance
            false,  // manageAttendanceRecords - correcting an entry is the Incharge's
            true,   // viewMonthlyReport
            true,   // fullAttendance
            true,   // createZoneRequest
            false,  // reviewZoneRequest
            false,  // administer
            false); // zoneScoped

    /**
     * The existing OFFICE_USER account, unchanged.
     *
     * <p>Not the same as the Office Sewadar designation, even though the names are
     * close. The fallback exists to keep accounts working exactly as they do today
     * until a designation is set on them; pointing it at the new Office Sewadar
     * grant would quietly hand every current Office User write access to the whole
     * register, which is a privilege escalation nobody asked for. Two tests catch
     * this, and did.</p>
     */
    private static final Grant OFFICE_READ_ONLY = new Grant(
            OPERATIONAL,
            false,  // manageSewadars
            false,  // manageBadges
            false,  // manageConstruction
            true,   // markAttendance
            false,  // manageAttendanceRecords
            false,  // viewMonthlyReport
            false,  // fullAttendance
            false,  // createZoneRequest
            false,  // reviewZoneRequest
            false,  // administer
            false); // zoneScoped

    /** Anyone else on the list: their own record, nothing more. */
    private static final Grant OWN_ONLY = new Grant(
            OWN_RECORD_ONLY,
            false, false, false, false, false, false, false, false, false, false, false);

    /**
      * Keyed on letters alone, so "Zone Incharge", "zone incharge", "Co-ordinator"
      * and "Coordinator" all find their row.
      *
      * <p>A hyphen decided who could do what until this was written down: the
      * designation on the record was "Co-ordinator" and the rule looked for
      * "coordinator", so the account fell through to the sewadar grant and lost its
      * zones. Spelling is not a permission.</p>
      */
    private static final Map<String, Grant> BY_DESIGNATION = Map.of(
            "coordinator", COORDINATOR,
            "zoneincharge", COORDINATOR,
            "supervisor", SUPERVISOR,
            "officeincharge", OFFICE_INCHARGE,
            "officesewadar", OFFICE_SEWADAR);

    /** Lower case, letters and digits only. */
    private static String key(String designation) {
        return designation.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Used only while a login still has no designation. */
    private static final Map<Role, Grant> BY_ROLE = Map.of(
            Role.ADMIN, ADMIN,
            Role.OFFICE_ADMIN, OFFICE_INCHARGE,
            Role.OFFICE_USER, OFFICE_READ_ONLY,
            Role.COORDINATOR, COORDINATOR,
            Role.ZONE_INCHARGE, COORDINATOR,
            Role.SUPERVISOR, SUPERVISOR,
            Role.SEWADAR, OWN_ONLY);

    private Capabilities() {
    }

    /**
     * What this person may do.
     *
     * @param role        the login's role; ADMIN wins regardless of designation
     * @param designation the designation on the linked sewadar record, or null
     */
    public static Grant of(Role role, String designation) {
        if (role == Role.ADMIN) {
            return ADMIN;
        }
        if (designation != null && !designation.isBlank()) {
            Grant grant = BY_DESIGNATION.get(key(designation.trim()));
            if (grant != null) {
                return grant;
            }
            // On the list but not in the matrix: an ordinary sewadar.
            return OWN_ONLY;
        }
        return BY_ROLE.getOrDefault(role, OWN_ONLY);
    }

    /** The menu, in the order the office reads it. */
    public static List<String> menu(Grant grant) {
        return List.of("HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT", "REQUEST",
                       "USERS", "SETUP", "CONTACT")
                .stream()
                .filter(grant.screens()::contains)
                .toList();
    }
}

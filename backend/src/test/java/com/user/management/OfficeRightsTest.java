package com.user.management;

import com.user.management.entity.Role;
import com.user.management.security.Capabilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may correct the register, and who may open the Monthly Report.
 *
 * <p>Two rights that used to travel with others and now stand on their own:</p>
 * <ul>
 *   <li><b>All Attendance Record</b> - marking a day is the office's daily work and
 *       most roles do it; going back over what was marked and altering or removing
 *       it is the Admin's and the Office Incharge's.</li>
 *   <li><b>The Monthly Report</b> - the whole register's report is the office's:
 *       Admin, Office Incharge and Office Sewadar. One named sewadar's hours stay
 *       open to anyone whose scope reaches them, which is a different screen.</li>
 * </ul>
 *
 * <p>The matrix is a plain table, so this is a plain table too: reading the test
 * next to {@code Capabilities} should make a disagreement between them obvious.</p>
 */
class OfficeRightsTest {

    @Test
    @DisplayName("correcting the register is the Admin's and the Office Incharge's")
    void whoMayCorrectTheRegister() {
        assertThat(grant(Role.ADMIN, null).manageAttendanceRecords()).isTrue();
        assertThat(grant(Role.OFFICE_USER, "Office Incharge").manageAttendanceRecords()).isTrue();
        // An Office Admin account with no designation falls back to the same grant.
        assertThat(grant(Role.OFFICE_ADMIN, null).manageAttendanceRecords()).isTrue();

        assertThat(grant(Role.OFFICE_USER, "Office Sewadar").manageAttendanceRecords()).isFalse();
        assertThat(grant(Role.OFFICE_USER, null).manageAttendanceRecords()).isFalse();
        assertThat(grant(Role.COORDINATOR, "Co-ordinator").manageAttendanceRecords()).isFalse();
        assertThat(grant(Role.ZONE_INCHARGE, "Zone Incharge").manageAttendanceRecords()).isFalse();
        assertThat(grant(Role.SUPERVISOR, "Supervisor").manageAttendanceRecords()).isFalse();
        assertThat(grant(Role.SEWADAR, "Sewadar").manageAttendanceRecords()).isFalse();
    }

    @Test
    @DisplayName("the Monthly Report is the office's three")
    void whoMayOpenTheMonthlyReport() {
        assertThat(grant(Role.ADMIN, null).viewMonthlyReport()).isTrue();
        assertThat(grant(Role.OFFICE_USER, "Office Incharge").viewMonthlyReport()).isTrue();
        assertThat(grant(Role.OFFICE_USER, "Office Sewadar").viewMonthlyReport()).isTrue();
        assertThat(grant(Role.OFFICE_ADMIN, null).viewMonthlyReport()).isTrue();

        assertThat(grant(Role.OFFICE_USER, null).viewMonthlyReport()).isFalse();
        assertThat(grant(Role.COORDINATOR, "Co-ordinator").viewMonthlyReport()).isFalse();
        assertThat(grant(Role.ZONE_INCHARGE, "Zone Incharge").viewMonthlyReport()).isFalse();
        assertThat(grant(Role.SUPERVISOR, "Supervisor").viewMonthlyReport()).isFalse();
        assertThat(grant(Role.SEWADAR, "Sewadar").viewMonthlyReport()).isFalse();
    }

    @Test
    @DisplayName("the Attendance module is the office three; everyone else marks and no more")
    void whoSeesTheWholeAttendanceModule() {
        assertThat(grant(Role.ADMIN, null).fullAttendance()).isTrue();
        assertThat(grant(Role.OFFICE_USER, "Office Incharge").fullAttendance()).isTrue();
        assertThat(grant(Role.OFFICE_USER, "Office Sewadar").fullAttendance()).isTrue();
        assertThat(grant(Role.OFFICE_ADMIN, null).fullAttendance()).isTrue();

        assertThat(grant(Role.COORDINATOR, "Co-ordinator").fullAttendance()).isFalse();
        assertThat(grant(Role.ZONE_INCHARGE, "Zone Incharge").fullAttendance()).isFalse();
        assertThat(grant(Role.SUPERVISOR, "Supervisor").fullAttendance()).isFalse();
        assertThat(grant(Role.OFFICE_USER, null).fullAttendance()).isFalse();

        // ... and the three who lose the tabs still mark the person in front of them.
        assertThat(grant(Role.COORDINATOR, "Co-ordinator").markAttendance()).isTrue();
        assertThat(grant(Role.ZONE_INCHARGE, "Zone Incharge").markAttendance()).isTrue();
        assertThat(grant(Role.SUPERVISOR, "Supervisor").markAttendance()).isTrue();
    }

    @Test
    @DisplayName("marking attendance is untouched by either of them")
    void markingIsStillWiderThanCorrecting() {
        // The point of splitting the two: everyone who marked yesterday still marks.
        assertThat(grant(Role.COORDINATOR, "Co-ordinator").markAttendance()).isTrue();
        assertThat(grant(Role.SUPERVISOR, "Supervisor").markAttendance()).isTrue();
        assertThat(grant(Role.OFFICE_USER, "Office Sewadar").markAttendance()).isTrue();
        assertThat(grant(Role.OFFICE_USER, null).markAttendance()).isTrue();
    }

    private Capabilities.Grant grant(Role role, String designation) {
        return Capabilities.of(role, designation);
    }
}

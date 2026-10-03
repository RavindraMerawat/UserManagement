package com.user.management;

import com.user.management.entity.Role;
import com.user.management.security.Capabilities;
import com.user.management.security.Capabilities.Grant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The permission matrix, asserted rather than described.
 *
 * <p>These are the rules the office asked for. If one of them changes, it should
 * change here first and deliberately - not as a side effect of editing a switch
 * somewhere in a service.</p>
 */
class CapabilitiesTest {

    private Grant designation(String name) {
        // Role deliberately not ADMIN, so the designation is what answers.
        return Capabilities.of(Role.SEWADAR, name);
    }

    @Test
    @DisplayName("The zone designations read their zones but do not change the register")
    void zoneDesignationsAreReadOnly() {
        for (String name : new String[] {"Coordinator", "Zone Incharge", "Supervisor"}) {
            Grant g = designation(name);
            assertThat(g.screens()).contains("HOME", "SEWADAR", "ATTENDANCE", "BADGES",
                                             "REPORT", "REQUEST");
            assertThat(g.manageSewadars()).as("%s adds sewadars", name).isFalse();
            assertThat(g.manageBadges()).as("%s issues badges", name).isFalse();
            assertThat(g.reviewZoneRequest()).as("%s approves requests", name).isFalse();
            assertThat(g.zoneScoped()).as("%s sees every zone", name).isTrue();
        }
    }

    @Test
    @DisplayName("Coordinator and Zone Incharge raise zone changes; Supervisor does not")
    void onlyTheSeniorTwoRaiseRequests() {
        assertThat(designation("Co-ordinator").createZoneRequest()).isTrue();
        assertThat(designation("Zone Incharge").createZoneRequest()).isTrue();
        assertThat(designation("Supervisor").createZoneRequest()).isFalse();
    }

    @Test
    @DisplayName("The office designations do the work but never decide a zone change")
    void officeCannotApprove() {
        for (String name : new String[] {"Office Incharge", "Office Sewadar"}) {
            Grant g = designation(name);
            assertThat(g.manageSewadars()).as("%s", name).isTrue();
            assertThat(g.manageBadges()).as("%s", name).isTrue();
            assertThat(g.createZoneRequest()).as("%s", name).isTrue();
            assertThat(g.reviewZoneRequest()).as("%s approves", name).isFalse();
        }
        // Account administration stays with the senior of the two.
        assertThat(designation("Office Incharge").administer()).isTrue();
        assertThat(designation("Office Sewadar").administer()).isFalse();
    }

    @Test
    @DisplayName("Only an Admin approves or rejects a zone change")
    void onlyAdminApproves() {
        assertThat(Capabilities.of(Role.ADMIN, null).reviewZoneRequest()).isTrue();
        assertThat(Capabilities.of(Role.OFFICE_ADMIN, "Office Incharge").reviewZoneRequest())
                .isFalse();
        assertThat(designation("Co-ordinator").reviewZoneRequest()).isFalse();
    }

    @Test
    @DisplayName("ADMIN wins whatever the designation says, so the office cannot lock itself out")
    void adminOverridesTheDesignation() {
        Grant g = Capabilities.of(Role.ADMIN, "Gate Incharge");
        assertThat(g.reviewZoneRequest()).isTrue();
        assertThat(g.administer()).isTrue();
        assertThat(g.screens()).contains("USERS", "SETUP");
    }

    @Test
    @DisplayName("A designation nobody wrote a rule for sees only its own record")
    void unlistedDesignationsSeeOnlyThemselves() {
        for (String name : new String[] {"Group Incharge", "Gate Incharge", "Sewadar",
                                         "Outer Incharge LS", "Side Filling Incharge"}) {
            Grant g = designation(name);
            assertThat(g.screens()).as("%s sees the register", name).doesNotContain("SEWADAR");
            assertThat(g.manageSewadars()).as("%s", name).isFalse();
            assertThat(g.createZoneRequest()).as("%s", name).isFalse();
        }
    }

    @Test
    @DisplayName("Designation names are matched whatever the casing or spacing")
    void namesAreMatchedLoosely() {
        assertThat(designation("  zone incharge  ").createZoneRequest()).isTrue();
        assertThat(designation("OFFICE INCHARGE").administer()).isTrue();
    }

    @Test
    @DisplayName("A login with no designation keeps exactly the access its role had")
    void theRoleFallbackDoesNotEscalate() {
        // The one that bit: Office User is read only, and must not inherit the new
        // Office Sewadar designation's write access just because the names are close.
        Grant officeUser = Capabilities.of(Role.OFFICE_USER, null);
        assertThat(officeUser.manageSewadars()).isFalse();
        assertThat(officeUser.manageBadges()).isFalse();

        assertThat(Capabilities.of(Role.OFFICE_ADMIN, null).manageSewadars()).isTrue();
        assertThat(Capabilities.of(Role.SUPERVISOR, null).createZoneRequest()).isFalse();
        assertThat(Capabilities.of(Role.SEWADAR, null).screens()).doesNotContain("SEWADAR");
    }

    @Test
    @DisplayName("The menu comes back in the order the office reads it")
    void menuIsOrdered() {
        assertThat(Capabilities.menu(Capabilities.of(Role.ADMIN, null)))
                .containsExactly("HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT",
                                 "REQUEST", "USERS", "SETUP", "CONTACT");
        assertThat(Capabilities.menu(designation("Co-ordinator")))
                .containsExactly("HOME", "SEWADAR", "ATTENDANCE", "BADGES", "REPORT",
                                 "REQUEST", "CONTACT");
    }

    @Test
    @DisplayName("a designation implies the account type the security rules match on")
    void designationsMapToAccountTypes() {
        assertThat(Role.forDesignation("Office Incharge")).isEqualTo(Role.OFFICE_ADMIN);
        assertThat(Role.forDesignation("Office Sewadar")).isEqualTo(Role.OFFICE_USER);
        assertThat(Role.forDesignation("Co-ordinator")).isEqualTo(Role.COORDINATOR);
        assertThat(Role.forDesignation("Zone Incharge")).isEqualTo(Role.ZONE_INCHARGE);
        assertThat(Role.forDesignation("Supervisor")).isEqualTo(Role.SUPERVISOR);
    }

    @Test
    @DisplayName("an unmapped designation is an ordinary sewadar, never something wider")
    void unmappedDesignationsDoNotEscalate() {
        for (String name : new String[] { "Gate Incharge", "Group Incharge", "Guide Sewadar",
                "Side Filling Incharge", "something the office adds later", "", null }) {
            assertThat(Role.forDesignation(name)).isEqualTo(Role.SEWADAR);
        }
    }

    @Test
    @DisplayName("the mapping ignores the casing and padding a typed-in name arrives with")
    void theMappingIsForgivingAboutFormatting() {
        assertThat(Role.forDesignation("  zone incharge  ")).isEqualTo(Role.ZONE_INCHARGE);
        assertThat(Role.forDesignation("OFFICE INCHARGE")).isEqualTo(Role.OFFICE_ADMIN);
    }
}

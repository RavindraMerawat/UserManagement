package com.user.management.security;

import com.user.management.entity.Role;
import com.user.management.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads the authenticated principal and turns its role into a {@link DataScope}.
 * Every service that returns sewadar, attendance or request data must scope its
 * queries with {@link #scope()}.
 */
@Service
public class CurrentUserService {

    public Optional<AppUserPrincipal> principalOrEmpty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppUserPrincipal p)) {
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public AppUserPrincipal principal() {
        return principalOrEmpty()
                .orElseThrow(() -> new ForbiddenException("No authenticated user in the current request"));
    }

    public String username() {
        return principalOrEmpty().map(AppUserPrincipal::getUsername).orElse("system");
    }

    /** What this caller may do, decided by designation with ADMIN overriding. */
    public Capabilities.Grant grant() {
        AppUserPrincipal p = principal();
        return Capabilities.of(p.getRole(), p.getDesignation());
    }

    /** The designation the rules were read from, for the UI to display. */
    public String designation() {
        return principal().getDesignation();
    }

    public Role role() {
        return principal().getRole();
    }

    /**
     * How much data this caller can reach.
     *
     * <p>Driven by the same grant as everything else: a zone designation sees its
     * own zones, the office designations see every zone, and anyone else sees only
     * their own record. A SEWADAR login is always held to its own record whatever
     * the designation says, because that account exists for exactly that.</p>
     */
    public DataScope scope() {
        AppUserPrincipal p = principal();
        if (p.getRole() == Role.SEWADAR) {
            return DataScope.selfOnly(p.getSewadarId());
        }
        Capabilities.Grant grant = grant();
        DataScope scope;
        if (grant.zoneScoped()) {
            scope = DataScope.zones(p.getZoneIds());
        } else if (grant.screens().contains("SEWADAR") || grant.manageSewadars()) {
            scope = DataScope.global();
        } else {
            scope = DataScope.selfOnly(p.getSewadarId());
        }
        /*
         * And then the account's own gender, on top of whatever reach the role gave
         * it: a male login reads the male register, a female login the female one.
         * Admin is the exception the office asked for - it sees both - and an
         * account with no gender set is unchanged, so turning this on takes nothing
         * away from an account until somebody fills the field in.
         */
        return p.getRole() == Role.ADMIN ? scope : scope.forGender(p.getGender());
    }

    /**
     * True when the caller may create, edit or delete sewadar master data.
     *
     * <p>Office work. The zone designations - Co-ordinator, Zone Incharge,
     * Supervisor - open the Sewadar screen read only.</p>
     */
    public boolean canManageSewadars() {
        return grant().manageSewadars();
    }

    /** True when the caller may mark or update attendance. */
    public boolean canMarkAttendance() {
        return grant().markAttendance();
    }

    /** May open All Attendance Record, and change or remove an entry on it. */
    public boolean canManageAttendanceRecords() {
        return grant().manageAttendanceRecords();
    }

    /** May open the Monthly Report. */
    public boolean canViewMonthlyReport() {
        return grant().viewMonthlyReport();
    }

    /** May open the whole Attendance module, not only Mark Attendance. */
    public boolean canUseFullAttendance() {
        return grant().fullAttendance();
    }

    /**
     * True when the caller may see a whole Aadhaar number rather than the masked
     * {@code XXXX XXXX 9012} form.
     *
     * <p>Admin and Office Admin may, because they are the only roles that register
     * and correct the number. A Sewadar may see their own, since it is theirs. Every
     * other role - Co-ordinator, Zone Incharge, Supervisor, Office User - works from
     * the badge number and gets the masked form, which is still enough to confirm a
     * card in someone's hand.</p>
     *
     * @param sewadarId the record being read, or null when that is not known
     */
    public boolean canViewFullAadhar(Long sewadarId) {
        // Whoever registers and corrects the number may read it in full; everyone
        // else works from the badge number and sees the masked form. A person may
        // always read their own.
        if (grant().manageSewadars()) {
            return true;
        }
        return sewadarId != null && sewadarId.equals(principal().getSewadarId());
    }

    /**
     * True when the role may issue a badge or record that one was collected.
     *
     * <p>Admin and Office Admin only. Co-ordinator, Zone Incharge and Supervisor can
     * open the Badge Detail screen and look up anyone in their zones, but handing out
     * a badge is an office action - so for them the screen is read only.</p>
     *
     * <p>Separate from {@link #canManageSewadars()} even though the two currently
     * agree: they answer different questions, and one is likely to move without the
     * other.</p>
     */
    public boolean canManageBadges() {
        return grant().manageBadges();
    }

    /** Record or update a construction sewa count. Office work, like a badge. */
    public boolean canManageConstruction() {
        return grant().manageConstruction();
    }

    /**
     * True when the caller may approve or reject a zone change request.
     *
     * <p>Admin alone. The office designations raise and read requests but do not
     * decide them.</p>
     */
    public boolean canReviewRequests() {
        return grant().reviewZoneRequest();
    }

    /** True when the caller may raise a zone change request. */
    public boolean canCreateZoneRequest() {
        return grant().createZoneRequest();
    }

    /** True when the caller may administer login accounts and the Setup lists. */
    public boolean canAdminister() {
        return grant().administer();
    }

    public void requireZoneAccess(Long zoneId) {
        if (!scope().allowsZone(zoneId)) {
            throw new ForbiddenException("You do not have access to zone " + zoneId);
        }
    }

    public void requireSewadarAccess(Long sewadarId, Long sewadarZoneId) {
        DataScope scope = scope();
        if (!scope.allowsSewadar(sewadarId) || !scope.allowsZone(sewadarZoneId)) {
            throw new ForbiddenException("You do not have access to this sewadar's records");
        }
    }
}

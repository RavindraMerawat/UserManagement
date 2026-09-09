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

    public Role role() {
        return principal().getRole();
    }

    /** Resolves the data scope for the current user from their role. */
    public DataScope scope() {
        AppUserPrincipal p = principal();
        Role role = p.getRole();
        if (role.isGlobalScope()) {
            return DataScope.global();
        }
        if (role.isZoneScope()) {
            return DataScope.zones(p.getZoneIds());
        }
        // SEWADAR: own records only.
        return DataScope.selfOnly(p.getSewadarId());
    }

    /** True when the role may create, edit or delete sewadar master data. */
    public boolean canManageSewadars() {
        return switch (role()) {
            case ADMIN, OFFICE_ADMIN -> true;
            default -> false;
        };
    }

    /** True when the role may mark or update attendance. */
    public boolean canMarkAttendance() {
        return switch (role()) {
            case ADMIN, OFFICE_ADMIN, COORDINATOR, ZONE_INCHARGE, SUPERVISOR -> true;
            default -> false;
        };
    }

    /** True when the role may approve or reject a zone change request. */
    public boolean canReviewRequests() {
        return switch (role()) {
            case ADMIN, OFFICE_ADMIN -> true;
            default -> false;
        };
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

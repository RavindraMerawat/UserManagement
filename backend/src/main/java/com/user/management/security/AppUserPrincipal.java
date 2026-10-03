package com.user.management.security;

import com.user.management.entity.Gender;
import com.user.management.entity.Role;
import com.user.management.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authenticated principal. Carries the role, the accessible zone ids and, for a
 * sewadar login, the linked sewadar id so the service layer can scope every query.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String fullName;
    private final String email;
    private final Role role;

    /**
     * Whose register this login reads, from the account. Null means both, which is
     * every account made before the field existed.
     */
    private final Gender gender;
    private final Set<Long> zoneIds;
    private final Long sewadarId;
    /**
     * The designation on the sewadar record linked to this login, or null when the
     * login has no sewadar or the record has no designation set yet. This is what
     * the permission rules read.
     */
    private final String designation;
    private final boolean enabled;
    private final boolean mustChangePassword;

    public AppUserPrincipal(User user, Long sewadarId, String designation) {
        this(user, sewadarId, designation, Set.of());
    }

    /**
      * @param coveredZoneIds zones the linked sewadar record covers, beyond the ones
      *                       on the account. A co-ordinator recorded as covering two
      *                       zones sees both, which is the point of recording it.
      */
    public AppUserPrincipal(User user, Long sewadarId, String designation,
                            Set<Long> coveredZoneIds) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.gender = user.getGender();
        Set<Long> zones = new java.util.LinkedHashSet<>(user.getZones().stream()
                .map(z -> z.getId())
                .toList());
        zones.addAll(coveredZoneIds);
        this.zoneIds = Set.copyOf(zones);
        this.sewadarId = sewadarId;
        this.designation = designation;
        this.enabled = user.isEnabled();
        this.mustChangePassword = user.isMustChangePassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    /** Whose register this login reads, or null for both. */
    public Gender getGender() {
        return gender;
    }

    public Role getRole() {
        return role;
    }

    public Set<Long> getZoneIds() {
        return zoneIds;
    }

    public Long getSewadarId() {
        return sewadarId;
    }

    /** Null until the linked sewadar record has a designation set on it. */
    public String getDesignation() {
        return designation;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}

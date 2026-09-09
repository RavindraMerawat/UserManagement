package com.user.management.security;

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
    private final Set<Long> zoneIds;
    private final Long sewadarId;
    private final boolean enabled;
    private final boolean mustChangePassword;

    public AppUserPrincipal(User user, Long sewadarId) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.zoneIds = user.getZones().stream()
                .map(z -> z.getId())
                .collect(Collectors.toUnmodifiableSet());
        this.sewadarId = sewadarId;
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

    public Role getRole() {
        return role;
    }

    public Set<Long> getZoneIds() {
        return zoneIds;
    }

    public Long getSewadarId() {
        return sewadarId;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}

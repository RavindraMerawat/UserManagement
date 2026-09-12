package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Login account. A {@link Sewadar} record may be linked to a user with role SEWADAR
 * so the sewadar can sign in and see only their own data.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_user_email", columnNames = "emailId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String username;

    @NotBlank
    @Column(name = "passwordHash", nullable = false, length = 100)
    private String passwordHash;

    @NotBlank
    @Column(name = "fullName", nullable = false, length = 150)
    private String fullName;

    @Email
    @Column(name = "emailId", length = 150)
    private String email;

    @Column(name = "mobileNo", length = 20)
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    /**
     * Zones this account may access. Empty for global-scope roles (ADMIN, OFFICE_ADMIN,
     * OFFICE_USER) and for SEWADAR, whose scope comes from the linked sewadar record.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_zones",
            joinColumns = @JoinColumn(name = "userId"),
            inverseJoinColumns = @JoinColumn(name = "zoneId"))
    private Set<Zone> zones = new LinkedHashSet<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /** Forces a password change on next login when true. */
    @Builder.Default
    @Column(name = "mustChangePassword", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "lastLoginAt")
    private Instant lastLoginAt;

    /** When the account photo was last uploaded, or null if there is none. */
    @Column(name = "photoUpdatedAt")
    private Instant photoUpdatedAt;

    public void addZone(Zone zone) {
        this.zones.add(zone);
    }
}

package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sewadars",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sewadar_badge", columnNames = "badgeNo"),
                @UniqueConstraint(name = "uk_sewadar_aadhar", columnNames = "aadharNo"),
                @UniqueConstraint(name = "uk_sewadar_email", columnNames = "emailId")
        },
        indexes = {
                @Index(name = "idx_sewadar_zone", columnList = "zoneId"),
                @Index(name = "idx_sewadar_name", columnList = "name"),
                @Index(name = "idx_sewadar_area", columnList = "area")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sewadar extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique sewadar identifier. Every attendance and report row is keyed off this. */
    @NotBlank
    @Column(name = "badgeNo", nullable = false, length = 40)
    private String badgeNumber;

    /** A badge number means a badge has been issued; receipt is confirmed separately. */
    @Builder.Default
    @Column(name = "badgeIssued", nullable = false)
    private boolean badgeIssued = false;

    @Builder.Default
    @Column(name = "badgeReceived", nullable = false)
    private boolean badgeReceived = false;

    /** Name */
    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    /** F/H Name - father or husband name. */
    @Column(name = "fatherOrHusbandName", length = 150)
    private String fatherOrHusbandName;

    /** Birth Date */
    @Column(name = "birthDate")
    private LocalDate dateOfBirth;

    /** Mobile No */
    @Column(name = "mobileNo", length = 20)
    private String mobile;

    /** Zone */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "zoneId", nullable = false)
    private Zone zone;

    /** Address */
    @Column(length = 400)
    private String address;

    /**
     * Aadhaar No. Stored as 12 digits with no spaces. Unique where present, and
     * nullable so a sewadar can be registered before the number is collected.
     *
     * <p>Stored as typed; what protects it is {@code AadharMask}, which hides all but
     * the last four digits from any role that has no reason to read the number.</p>
     */
    @Column(name = "aadharNo", length = 12)
    private String aadharNumber;

    /** Blood Group */
    @Column(name = "bloodGroup", length = 10)
    private String bloodGroup;

    /** Area the sewadar belongs to, inside the zone. */
    @Column(length = 120)
    private String area;

    /** Center / Point the sewadar reports to. */
    @Column(name = "point", length = 120)
    private String centerPoint;

    // ---- additional details, kept for reporting and contact ----

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Email
    @Column(name = "emailId", length = 150)
    private String email;

    @Column(length = 80)
    private String city;

    @Column(length = 10)
    private String pincode;

    /** Department or sewa group inside the zone. Appears on every report. */
    @Column(length = 120)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "primarySewaType", length = 30)
    private SewaType primarySewaType;

    @Column(name = "joiningDate")
    private LocalDate joiningDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /**
     * When the sewadar photo was last uploaded, or null if there is none. The image
     * bytes live in the photos table so grid queries never load them.
     */
    @Column(name = "photoUpdatedAt")
    private Instant photoUpdatedAt;

    /** Login account for this sewadar, created on demand. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", unique = true)
    private User user;
}

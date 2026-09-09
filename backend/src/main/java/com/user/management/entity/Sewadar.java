package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "sewadars",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sewadar_badge", columnNames = "badge_number"),
                @UniqueConstraint(name = "uk_sewadar_aadhar", columnNames = "aadhar_number")
        },
        indexes = {
                @Index(name = "idx_sewadar_zone", columnList = "zone_id"),
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
    @Column(name = "badge_number", nullable = false, length = 40)
    private String badgeNumber;

    /** Name */
    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    /** F/H Name - father or husband name. */
    @Column(name = "father_or_husband_name", length = 150)
    private String fatherOrHusbandName;

    /** Birth Date */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Mobile No */
    @Column(length = 20)
    private String mobile;

    /** Zone */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    /** Address */
    @Column(length = 400)
    private String address;

    /**
     * Aadhaar No. Stored as 12 digits with no spaces. Unique where present, and
     * nullable so a sewadar can be registered before the number is collected.
     */
    @Column(name = "aadhar_number", length = 12)
    private String aadharNumber;

    /** Blood Group */
    @Column(name = "blood_group", length = 10)
    private String bloodGroup;

    /** Area the sewadar belongs to, inside the zone. */
    @Column(length = 120)
    private String area;

    /** Center / Point the sewadar reports to. */
    @Column(name = "center_point", length = 120)
    private String centerPoint;

    // ---- additional details, kept for reporting and contact ----

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Email
    @Column(length = 150)
    private String email;

    @Column(length = 80)
    private String city;

    @Column(length = 10)
    private String pincode;

    /** Department or sewa group inside the zone. Appears on every report. */
    @Column(length = 120)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_sewa_type", length = 30)
    private SewaType primarySewaType;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Login account for this sewadar, created on demand. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;
}

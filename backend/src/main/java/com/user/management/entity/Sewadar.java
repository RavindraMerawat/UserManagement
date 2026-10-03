package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

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

    /**
     * Excused from attendance. An exempted sewadar is still on the roster and still
     * appears in the reports; the flag records that their absence is expected, so a
     * low attendance figure against their name is not a finding.
     */
    @Column(nullable = false)
    private boolean exempted = false;

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

    /**
     * Age in years, as recorded on the registration slip.
     *
     * <p>Kept alongside the birth date rather than worked out from it: the office
     * often has one and not the other, and a record with an age but no date of
     * birth is normal here. Nothing recalculates this - it is what was written
     * down, not a derived value that drifts as the years pass.</p>
     */
    @Column
    private Integer age;

    /** Mobile No */
    @Column(name = "mobileNo", length = 40)
    private String mobile;

    /**
      * The zone this sewadar belongs to. One, always.
      *
      * <p>Everything downstream reads this - their attendance is filed under it,
      * the reports group by it, and a zone-scoped role sees them through it - so it
      * stays single even for a co-ordinator who covers several.</p>
      */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "zoneId", nullable = false)
    private Zone zone;

    /**
      * The other zones a co-ordinator covers, beyond their own.
      *
      * <p>Empty for everybody else. This records the reach of the role, not where
      * the person belongs, which is why it sits beside {@link #zone} rather than
      * replacing it: a sewadar filed under two zones would be counted twice in
      * every report that groups by zone.</p>
      */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "sewadarZones",
            joinColumns = @JoinColumn(name = "sewadarId"),
            inverseJoinColumns = @JoinColumn(name = "zoneId"))
    @Builder.Default
    private Set<Zone> extraZones = new LinkedHashSet<>();

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

    /**
      * The grouping inside the area, where the area has them.
      *
      * <p>Only Indore's areas are grouped, so the screens offer this field there and
      * nowhere else. It is free text because the groupings are not a list anybody
      * maintains yet.</p>
      */
    /*
     * Backticked because GROUPING is a reserved word in MySQL 8 - unquoted, every
     * insert into this table fails with a syntax error. H2 does not reserve it, so
     * the tests stay green either way; only MySQL tells you.
     */
    @Column(name = "`grouping`", length = 120)
    private String grouping;

    /** Local or Outstation - whether they travel in for the sewa. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Locality locality;

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

    /** Department or sewa group inside the zone. Appears on every report. */
    @Column(length = 120)
    private String department;

    /**
     * Permanent or Open. Replaced the old primary sewa type, which described work
     * rather than the person.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SewadarStatus status;

    /**
     * What this person does in the organisation - shown on the screens as
     * "Designation", stored in the roles table. This is the field the permission
     * rules read, through the login linked to this record.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "roleId")
    private SewadarRole role;

    /** The post they are put on, which is not the same as where they belong. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sewaPointId")
    private SewaPoint sewaPoint;

    @Column(name = "joiningDate")
    private LocalDate joiningDate;

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

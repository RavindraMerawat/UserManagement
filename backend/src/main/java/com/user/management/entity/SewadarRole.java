package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * What a sewadar does in the organisation - Co-ordinator, Zone Incharge, Supervisor
 * and the rest. Stored in the <b>roles</b> table and shown on the screens as
 * "Designation", which is the word the office uses for it.
 *
 * <p>The Java type is not called {@code Role} only because the login-account enum
 * already owns that name in this package. The table, the foreign key and every
 * rule below are the role.</p>
 *
 * <p>This is the field the application reads to decide what a person may see and do.
 * A login's designation is the designation on the sewadar record linked to it; the
 * ADMIN role remains an override above all of them, so the office can never lock
 * itself out by editing this list.</p>
 *
 * <p>The rows are seeded on first start and are meant to be a fixed vocabulary. They
 * can be edited from Setup, but renaming one changes what every sewadar holding it
 * is called - it does not move anybody between designations.</p>
 */
@Entity
@Table(name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_name",
                columnNames = {"name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SewadarRole extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String name;

    /**
     * Retired rather than deleted once sewadars hold it, the same rule zones and
     * areas follow, so existing records keep resolving.
     */
    @Column(nullable = false)
    private boolean active = true;
}

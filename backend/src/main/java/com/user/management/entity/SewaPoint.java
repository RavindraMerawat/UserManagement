package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Where a sewadar performs their sewa.
 *
 * <p>Deliberately separate from {@link SatsangPoint}: a satsang point is a place in
 * the zone/area geography and is where a sewadar belongs, while a sewa point is the
 * post they are put on. The two are chosen independently, which is why this list is
 * flat rather than hanging off a zone.</p>
 */
@Entity
@Table(name = "sewaPoints",
        uniqueConstraints = @UniqueConstraint(name = "uk_sewaPoint_name",
                columnNames = {"name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SewaPoint extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}

package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * A satsang point inside an area - the place a sewadar actually reports to, and the
 * third level of the office's geography after zone and area.
 */
@Entity
@Table(name = "satsang_points",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_area_name",
                columnNames = {"areaId", "name"}),
        indexes = @Index(name = "idx_point_area", columnList = "areaId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SatsangPoint extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    /** The area this point sits in; its zone follows from the area. */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "areaId", nullable = false)
    private Area area;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

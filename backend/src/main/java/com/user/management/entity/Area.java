package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * An area inside a zone - the second level of the office's geography, between the
 * zone and the satsang point.
 *
 * <p>Set up on the Setup screen so the sewadar form offers a list rather than a free
 * text box, which is what stopped "Geeta Vihar" and "geeta vihar" being two different
 * places.</p>
 */
@Entity
@Table(name = "areas",
        uniqueConstraints = @UniqueConstraint(name = "uk_area_zone_name",
                columnNames = {"zoneId", "name"}),
        indexes = @Index(name = "idx_area_zone", columnList = "zoneId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Area extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    /** The zone this area belongs to. An area never spans two zones. */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "zoneId", nullable = false)
    private Zone zone;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

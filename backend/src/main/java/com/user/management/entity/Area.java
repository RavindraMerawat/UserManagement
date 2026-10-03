package com.user.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An area, as a list of its own.
 *
 * <p>It used to belong to a zone, which made the Add Sewadar form narrow the area
 * picker by whichever zone was chosen. The office keeps areas, zones and satsang
 * points as three separate lists, so the area no longer knows about zones and the
 * name stands alone.</p>
 */
@Entity
@Table(name = "areas",
        uniqueConstraints = @UniqueConstraint(name = "uk_area_name", columnNames = "name"))
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

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

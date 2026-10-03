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
 * A satsang point, as a list of its own.
 *
 * <p>It used to sit inside an area, so the sewadar form would not offer a point
 * until an area had been chosen. Points are now their own list, picked in any
 * order.</p>
 */
@Entity
@Table(name = "satsang_points",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_name", columnNames = "name"))
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

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

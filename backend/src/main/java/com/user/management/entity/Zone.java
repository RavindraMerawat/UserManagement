package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "zones", uniqueConstraints = @UniqueConstraint(name = "uk_zone_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 40)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    /** Optional grouping such as a city or centre the zone belongs to. */
    @Column(length = 120)
    private String centre;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}

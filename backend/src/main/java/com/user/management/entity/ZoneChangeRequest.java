package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

/**
 * Request to move a sewadar from one zone to another. Raised by the sewadar,
 * a zone incharge or a supervisor; approved by ADMIN or OFFICE_ADMIN.
 */
@Entity
@Table(name = "zone_change_requests", indexes = {
        @Index(name = "idx_zcr_status", columnList = "status"),
        @Index(name = "idx_zcr_sewadar", columnList = "sewadar_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneChangeRequest extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sewadar_id", nullable = false)
    private Sewadar sewadar;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_zone_id", nullable = false)
    private Zone fromZone;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_zone_id", nullable = false)
    private Zone toZone;

    @Column(length = 500)
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "requested_by", length = 60)
    private String requestedBy;

    @Column(name = "reviewed_by", length = 60)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;
}

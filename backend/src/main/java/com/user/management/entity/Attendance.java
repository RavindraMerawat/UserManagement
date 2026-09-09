package com.user.management.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One attendance record per sewadar, per date, per sewa type.
 */
@Entity
@Table(name = "attendance",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_sewadar_date_type",
                columnNames = {"sewadar_id", "attendance_date", "sewa_type"}),
        indexes = {
                @Index(name = "idx_attendance_date", columnList = "attendance_date"),
                @Index(name = "idx_attendance_zone_date", columnList = "zone_id,attendance_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sewadar_id", nullable = false)
    private Sewadar sewadar;

    /**
     * Zone the sewa was performed in. Denormalised from the sewadar so historic records
     * stay correct after a zone change.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @NotNull
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sewa_type", nullable = false, length = 30)
    private SewaType sewaType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "in_time")
    private LocalTime inTime;

    @Column(name = "out_time")
    private LocalTime outTime;

    /** Hours of sewa. Derived from in/out time when both are present. */
    @Column(name = "hours")
    private Double hours;

    @Column(length = 400)
    private String remarks;

    /** Username of the account that marked or last updated this record. */
    @Column(name = "marked_by", length = 60)
    private String markedBy;

    @PrePersist
    @PreUpdate
    void deriveHours() {
        if (inTime != null && outTime != null && outTime.isAfter(inTime)) {
            long minutes = Duration.between(inTime, outTime).toMinutes();
            this.hours = Math.round((minutes / 60.0) * 100.0) / 100.0;
        }
    }
}

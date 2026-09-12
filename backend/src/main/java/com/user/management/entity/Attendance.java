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
                columnNames = {"sewadarId", "attendanceDate", "sewaType"}),
        indexes = {
                @Index(name = "idx_attendance_date", columnList = "attendanceDate"),
                @Index(name = "idx_attendance_zone_date", columnList = "zoneId,attendanceDate")
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
    @JoinColumn(name = "sewadarId", nullable = false)
    private Sewadar sewadar;

    /**
     * Zone the sewa was performed in. Denormalised from the sewadar so historic records
     * stay correct after a zone change.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "zoneId", nullable = false)
    private Zone zone;

    @NotNull
    @Column(name = "attendanceDate", nullable = false)
    private LocalDate attendanceDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sewaType", nullable = false, length = 30)
    private SewaType sewaType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "inTime")
    private LocalTime inTime;

    @Column(name = "outTime")
    private LocalTime outTime;

    /** Hours of sewa. Derived from in/out time when both are present. */
    @Column(name = "hours")
    private Double hours;

    @Column(length = 400)
    private String remarks;

    /** Username of the account that marked or last updated this record. */
    @Column(name = "markedBy", length = 60)
    private String markedBy;

    /**
     * Recomputes {@link #hours} from the in and out time.
     *
     * <p>Call this from the service before returning a saved entity. Relying on the
     * lifecycle callback alone is not enough: Hibernate fires {@code @PreUpdate} at
     * flush time, which is after the response DTO has already been built, so an
     * updated record would report stale hours to the caller even though the database
     * ends up correct.</p>
     */
    public void recalculateHours() {
        if (inTime != null && outTime != null && outTime.isAfter(inTime)) {
            long minutes = Duration.between(inTime, outTime).toMinutes();
            this.hours = Math.round((minutes / 60.0) * 100.0) / 100.0;
        }
    }

    /** Safety net for any path that writes the entity without going through a service. */
    @PrePersist
    @PreUpdate
    void deriveHours() {
        recalculateHours();
    }
}

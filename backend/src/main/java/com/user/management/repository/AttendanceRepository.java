package com.user.management.repository;

import com.user.management.entity.Attendance;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;
import com.user.management.repository.projection.MonthlySummaryRow;
import com.user.management.repository.projection.SewaTypeCountRow;
import com.user.management.repository.projection.StatusCountRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findBySewadarIdAndAttendanceDateAndSewaType(Long sewadarId,
                                                                     LocalDate attendanceDate,
                                                                     SewaType sewaType);

    List<Attendance> findBySewadarIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(Long sewadarId,
                                                                                      LocalDate from,
                                                                                      LocalDate to);

    List<Attendance> findByAttendanceDateAndZoneId(LocalDate date, Long zoneId);

    long countByAttendanceDateAndStatus(LocalDate date, AttendanceStatus status);

    long countByAttendanceDateAndStatusAndZoneIdIn(LocalDate date,
                                                   AttendanceStatus status,
                                                   Collection<Long> zoneIds);

    /**
     * Paged attendance listing. Every filter is optional; {@code zoneIds} and
     * {@code sewadarScopeId} carry the caller's data scope and are applied on top of
     * the user supplied filters.
     */
    @Query("""
            select a from Attendance a
            where (:zoneIds is null or a.zone.id in :zoneIds)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
              and (:sewadarId is null or a.sewadar.id = :sewadarId)
              and (:zoneId is null or a.zone.id = :zoneId)
              and (:sewaType is null or a.sewaType = :sewaType)
              and (:status is null or a.status = :status)
              and (:from is null or a.attendanceDate >= :from)
              and (:to is null or a.attendanceDate <= :to)
            """)
    Page<Attendance> search(@Param("sewadarId") Long sewadarId,
                            @Param("zoneId") Long zoneId,
                            @Param("sewaType") SewaType sewaType,
                            @Param("status") AttendanceStatus status,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("zoneIds") Collection<Long> zoneIds,
                            @Param("sewadarScopeId") Long sewadarScopeId,
                            Pageable pageable);

    /** Same filters as {@link #search} but unpaged, for export and sharing. */
    @Query("""
            select a from Attendance a
            where (:zoneIds is null or a.zone.id in :zoneIds)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
              and (:sewadarId is null or a.sewadar.id = :sewadarId)
              and (:zoneId is null or a.zone.id = :zoneId)
              and (:sewaType is null or a.sewaType = :sewaType)
              and (:from is null or a.attendanceDate >= :from)
              and (:to is null or a.attendanceDate <= :to)
            order by a.sewadar.name asc, a.attendanceDate asc
            """)
    List<Attendance> findForReport(@Param("sewadarId") Long sewadarId,
                                   @Param("zoneId") Long zoneId,
                                   @Param("sewaType") SewaType sewaType,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("zoneIds") Collection<Long> zoneIds,
                                   @Param("sewadarScopeId") Long sewadarScopeId);

    /** Per-sewadar aggregation behind the monthly attendance report. */
    @Query("""
            select s.id as sewadarId,
                   s.badgeNumber as badgeNumber,
                   s.name as sewadarName,
                   z.name as zoneName,
                   coalesce(s.department, '') as department,
                   count(a.id) as totalRecords,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.PRESENT then 1 else 0 end) as presentDays,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.HALF_DAY then 1 else 0 end) as halfDays,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.LEAVE then 1 else 0 end) as leaveDays,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.ABSENT then 1 else 0 end) as absentDays,
                   sum(case when a.sewaType = com.user.management.entity.SewaType.ROSTER_SEWA then 1 else 0 end) as rosterSewaDays,
                   sum(case when a.sewaType = com.user.management.entity.SewaType.CONSTRUCTION_SEWA then 1 else 0 end) as constructionSewaDays,
                   coalesce(sum(a.hours), 0.0) as totalHours
            from Attendance a
              join a.sewadar s
              join a.zone z
            where a.attendanceDate between :from and :to
              and (:zoneIds is null or z.id in :zoneIds)
              and (:sewadarScopeId is null or s.id = :sewadarScopeId)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:zoneId is null or z.id = :zoneId)
              and (:sewaType is null or a.sewaType = :sewaType)
            group by s.id, s.badgeNumber, s.name, z.name, s.department
            order by s.name asc
            """)
    List<MonthlySummaryRow> monthlySummary(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("sewadarId") Long sewadarId,
                                           @Param("zoneId") Long zoneId,
                                           @Param("sewaType") SewaType sewaType,
                                           @Param("zoneIds") Collection<Long> zoneIds,
                                           @Param("sewadarScopeId") Long sewadarScopeId);

    @Query("""
            select a.status as status, count(a.id) as count
            from Attendance a
            where a.attendanceDate between :from and :to
              and (:zoneIds is null or a.zone.id in :zoneIds)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
            group by a.status
            """)
    List<StatusCountRow> countByStatus(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("zoneIds") Collection<Long> zoneIds,
                                       @Param("sewadarScopeId") Long sewadarScopeId);

    @Query("""
            select a.sewaType as sewaType, count(a.id) as count
            from Attendance a
            where a.attendanceDate between :from and :to
              and (:zoneIds is null or a.zone.id in :zoneIds)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
            group by a.sewaType
            """)
    List<SewaTypeCountRow> countBySewaType(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("zoneIds") Collection<Long> zoneIds,
                                           @Param("sewadarScopeId") Long sewadarScopeId);
}

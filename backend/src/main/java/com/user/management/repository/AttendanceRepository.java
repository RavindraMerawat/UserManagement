package com.user.management.repository;

import com.user.management.entity.Gender;
import com.user.management.entity.Attendance;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Locality;
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

    /**
     * One day's rows for a page of sewadars, for the Today Hours column.
     *
     * <p>Asked once for the whole page rather than once per row: the dashboard list
     * shows twenty-five people and a query each would be twenty-five round trips for
     * one column. The ids come from a page that is already narrowed to the caller's
     * scope, so this adds no reach of its own.</p>
     */
    List<Attendance> findBySewadarIdInAndAttendanceDate(Collection<Long> sewadarIds, LocalDate date);

    long countByAttendanceDateAndStatus(LocalDate date, AttendanceStatus status);

    long countByAttendanceDateAndStatusAndZoneIdIn(LocalDate date,
                                                   AttendanceStatus status,
                                                   Collection<Long> zoneIds);

    /**
     * Paged attendance listing. Every filter is optional; {@code zoneIds} and
     * {@code sewadarScopeId} carry the caller's data scope and are applied on top of
     * the user supplied filters.
     *
     * <p>{@code q} is the office's one box over GR. No, name and mobile - whichever
     * of the three is to hand when somebody is standing at the desk. It narrows the
     * same list the date and zone filters narrow rather than replacing them.</p>
     */
    @Query("""
            select a from Attendance a
            where (:zoneIds is null or a.zone.id in :zoneIds)
              and (:scopeGender is null or a.sewadar.gender = :scopeGender)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
              and (:sewadarId is null or a.sewadar.id = :sewadarId)
              and (:zoneId is null or a.zone.id = :zoneId)
              and (:sewaType is null or a.sewaType = :sewaType)
              and (:status is null or a.status = :status)
              and (:from is null or a.attendanceDate >= :from)
              and (:to is null or a.attendanceDate <= :to)
              and (:q is null or (lower(a.sewadar.badgeNumber) like %:q%
                   or lower(a.sewadar.name) like %:q%
                   or lower(coalesce(a.sewadar.mobile, '')) like %:q%))
            """)
    Page<Attendance> search(@Param("sewadarId") Long sewadarId,
                            @Param("zoneId") Long zoneId,
                            @Param("sewaType") SewaType sewaType,
                            @Param("status") AttendanceStatus status,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("q") String q,
                            @Param("zoneIds") Collection<Long> zoneIds,
                            @Param("scopeGender") Gender scopeGender,
                            @Param("sewadarScopeId") Long sewadarScopeId,
                            Pageable pageable);

    /** Same filters as {@link #search} but unpaged, for export and sharing. */
    @Query("""
            select a from Attendance a
            where (:zoneIds is null or a.zone.id in :zoneIds)
              and (:scopeGender is null or a.sewadar.gender = :scopeGender)
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
                                   @Param("scopeGender") Gender scopeGender,
                                   @Param("sewadarScopeId") Long sewadarScopeId);

    /**
     * Per-sewadar aggregation behind the monthly hours sheet.
     *
     * <p>It starts from the sewadars and reaches out to their attendance, not the
     * other way round. Somebody who did no sewa all month still belongs on the sheet
     * at zero - that is half of what the office reads it for - and an attendance-first
     * query has no row to put them on.</p>
     *
     * <p>Because of that the zone is the sewadar's own, not the zone each attendance
     * was marked in. It is the only one a person with no attendance has, and it is the
     * one the sheet is organised by.</p>
     */
    @Query("""
            select s.id as sewadarId,
                   s.badgeNumber as badgeNumber,
                   s.name as sewadarName,
                   z.name as zoneName,
                   coalesce(s.area, '') as area,
                   coalesce(s.centerPoint, '') as satsangPoint,
                   coalesce(s.department, '') as department,
                   coalesce(s.grouping, '') as groupingName,
                   coalesce(rl.name, '') as designation,
                   s.status as status,
                   s.dateOfBirth as birthDate,
                   s.exempted as exempted,
                   count(a.id) as totalRecords,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.PRESENT then 1 else 0 end) as presentDays,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.HALF_DAY then 1 else 0 end) as halfDays,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.LEAVE then 1 else 0 end) as leaveDays,
                   sum(case when a.status = com.user.management.entity.AttendanceStatus.ABSENT then 1 else 0 end) as absentDays,
                   sum(case when a.sewaType = com.user.management.entity.SewaType.DAILY_SEWA then 1 else 0 end) as dailySewaDays,
                   sum(case when a.sewaType = com.user.management.entity.SewaType.CONSTRUCTION_SEWA then 1 else 0 end) as constructionSewaDays,
                   coalesce(sum(a.hours), 0.0) as totalHours
            from Sewadar s
              join s.zone z
              left join Attendance a
                on a.sewadar = s
               and a.attendanceDate between :from and :to
               and (:sewaType is null or a.sewaType = :sewaType)
              left join s.role rl
            where (:zoneIds is null or z.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarScopeId is null or s.id = :sewadarScopeId)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:zoneId is null or z.id = :zoneId)
              and (:designationId is null or rl.id = :designationId)
              and (:locality is null or s.locality = :locality)
            group by s.id, s.badgeNumber, s.name, z.name, s.area, s.centerPoint,
                     s.department, s.grouping, rl.name, s.status, s.dateOfBirth, s.exempted
            order by s.name asc
            """)
    List<MonthlySummaryRow> monthlySummary(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("sewadarId") Long sewadarId,
                                           @Param("zoneId") Long zoneId,
                                           @Param("sewaType") SewaType sewaType,
                                           @Param("designationId") Long designationId,
                                           @Param("locality") Locality locality,
                                           @Param("zoneIds") Collection<Long> zoneIds,
                                           @Param("scopeGender") Gender scopeGender,
                                           @Param("sewadarScopeId") Long sewadarScopeId);

    /**
     * Present and absent days per calendar month, for the dashboard chart.
     *
     * <p>This counts attendance <b>records</b>, not people: "March had 412 present
     * days" is the question a monthly attendance chart answers. The tiles above it
     * count people, which is a different question - the chart is labelled so the two
     * are not read as the same number.</p>
     */
    @Query("""
            select month(a.attendanceDate) as month, a.status as status, count(a.id) as count
            from Attendance a
            where a.attendanceDate between :from and :to
              and (:zoneIds is null or a.zone.id in :zoneIds)
                and (:scopeGender is null or a.sewadar.gender = :scopeGender)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
            group by month(a.attendanceDate), a.status
            order by month(a.attendanceDate)
            """)
    List<MonthlyStatusRow> countByMonthAndStatus(@Param("from") LocalDate from,
                                                 @Param("to") LocalDate to,
                                                 @Param("zoneIds") Collection<Long> zoneIds,
                                                 @Param("scopeGender") Gender scopeGender,
                                                 @Param("sewadarScopeId") Long sewadarScopeId);

    /** One (month, status) bucket of {@link #countByMonthAndStatus}. */
    interface MonthlyStatusRow {
        int getMonth();

        AttendanceStatus getStatus();

        long getCount();
    }

    @Query("""
            select a.status as status, count(a.id) as count
            from Attendance a
            where a.attendanceDate between :from and :to
              and (:zoneIds is null or a.zone.id in :zoneIds)
                and (:scopeGender is null or a.sewadar.gender = :scopeGender)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
            group by a.status
            """)
    List<StatusCountRow> countByStatus(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("zoneIds") Collection<Long> zoneIds,
                                       @Param("scopeGender") Gender scopeGender,
                                       @Param("sewadarScopeId") Long sewadarScopeId);

    @Query("""
            select a.sewaType as sewaType, count(a.id) as count
            from Attendance a
            where a.attendanceDate between :from and :to
              and (:zoneIds is null or a.zone.id in :zoneIds)
                and (:scopeGender is null or a.sewadar.gender = :scopeGender)
              and (:sewadarScopeId is null or a.sewadar.id = :sewadarScopeId)
            group by a.sewaType
            """)
    List<SewaTypeCountRow> countBySewaType(@Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("zoneIds") Collection<Long> zoneIds,
                                           @Param("scopeGender") Gender scopeGender,
                                           @Param("sewadarScopeId") Long sewadarScopeId);
}

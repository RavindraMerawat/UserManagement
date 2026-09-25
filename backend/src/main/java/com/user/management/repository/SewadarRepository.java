package com.user.management.repository;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Gender;
import com.user.management.repository.projection.GenderCount;
import com.user.management.entity.Sewadar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SewadarRepository extends JpaRepository<Sewadar, Long> {

    Optional<Sewadar> findByBadgeNumberIgnoreCase(String badgeNumber);

    boolean existsByBadgeNumberIgnoreCase(String badgeNumber);

    /** Aadhaar numbers are stored as bare digits, so an exact match is enough. */
    boolean existsByAadharNumber(String aadharNumber);

    /** Email is optional, but where it is given it identifies one person. */
    boolean existsByEmailIgnoreCase(String email);

    Optional<Sewadar> findByUserId(Long userId);

    long countByActiveTrue();

    long countByZoneIdInAndActiveTrue(Collection<Long> zoneIds);

    @Query("""
            select count(s) from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:issued is null or s.badgeIssued = :issued)
              and (:received is null or s.badgeReceived = :received)
            """)
    long countBadges(@Param("zoneIds") Collection<Long> zoneIds,
                     @Param("sewadarId") Long sewadarId,
                     @Param("issued") Boolean issued,
                     @Param("received") Boolean received);

    /** Sewadars in scope, optionally narrowed to active or inactive, for the tabs. */
    @Query("""
            select count(s) from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:active is null or s.active = :active)
            """)
    long countInScope(@Param("active") Boolean active,
                      @Param("zoneIds") Collection<Long> zoneIds,
                      @Param("sewadarId") Long sewadarId);

    /**
     * Active sewadars that already existed on a given date, from the audit stamp.
     *
     * <p>Used for the "vs last month" figure on the Total tile. There is no history
     * table, so this is the honest approximation available: rows created on or before
     * that date and still active. A sewadar deactivated since will not be counted,
     * which is the right answer for "how many did we have" but not for "how many rows
     * existed" - the tile means the former.</p>
     */
    /**
     * The same population as {@link #countForDashboard}, grouped by gender.
     *
     * <p>One query rather than one per gender: the dashboard needs male and female
     * for three metrics across two periods, and asking separately would be a dozen
     * round trips for numbers the database can group in one pass. Sewadars with no
     * gender recorded are grouped under a null key and counted in neither card.</p>
     */
    @Query("""
            select s.gender as gender, count(s) as count from Sewadar s
            where s.active = true
              and (:zoneIds is null or s.zone.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:status is null or exists (
                    select a.id from Attendance a
                    where a.sewadar = s
                      and a.attendanceDate = :onDate
                      and a.status = :status))
            group by s.gender
            """)
    List<GenderCount> countForDashboardByGender(@Param("status") AttendanceStatus status,
                                                @Param("onDate") LocalDate onDate,
                                                @Param("zoneIds") Collection<Long> zoneIds,
                                                @Param("sewadarId") Long sewadarId);

    @Query("""
            select count(s) from Sewadar s
            where s.active = true
              and s.createdAt <= :asOf
              and (:zoneIds is null or s.zone.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
            """)
    long countActiveAsOf(@Param("asOf") Instant asOf,
                         @Param("zoneIds") Collection<Long> zoneIds,
                         @Param("sewadarId") Long sewadarId);

    /*
     * The dashboard pair. A tile and the grid behind it must never disagree, so the
     * count and the list are the same WHERE clause written twice - change one and you
     * have to change the other, which is visible, rather than two different
     * definitions drifting apart quietly.
     *
     * `exists` rather than a join: attendance is one row per sewadar per date per
     * sewa type, so somebody marked present for both Roster and Construction sewa on
     * one day would otherwise be counted, and listed, twice. These count people.
     *
     * A null status means "every active sewadar in scope", which is the Total tile.
     */

    @Query("""
            select s from Sewadar s
            join s.zone z
            where s.active = true
              and (:zoneIds is null or z.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:gender is null or s.gender = :gender)
              and (:status is null or exists (
                    select a.id from Attendance a
                    where a.sewadar = s
                      and a.attendanceDate = :onDate
                      and a.status = :status))
            """)
    Page<Sewadar> findForDashboard(@Param("status") AttendanceStatus status,
                                   @Param("onDate") LocalDate onDate,
                                   @Param("zoneIds") Collection<Long> zoneIds,
                                   @Param("sewadarId") Long sewadarId,
                                   @Param("gender") Gender gender,
                                   Pageable pageable);

    @Query("""
            select count(s) from Sewadar s
            where s.active = true
              and (:zoneIds is null or s.zone.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:status is null or exists (
                    select a.id from Attendance a
                    where a.sewadar = s
                      and a.attendanceDate = :onDate
                      and a.status = :status))
            """)
    long countForDashboard(@Param("status") AttendanceStatus status,
                           @Param("onDate") LocalDate onDate,
                           @Param("zoneIds") Collection<Long> zoneIds,
                           @Param("sewadarId") Long sewadarId);

    /**
     * Search restricted by an optional zone id list. A null {@code zoneIds} means no
     * zone restriction (global-scope roles); a null {@code sewadarId} means no
     * self-only restriction.
     */
    @Query("""
            select s from Sewadar s
            join s.zone z
            where (:zoneIds is null or z.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:zoneId is null or z.id = :zoneId)
              and (:active is null or s.active = :active)
              and (:q is null or lower(s.name) like %:q%
                   or lower(s.badgeNumber) like %:q%
                   or lower(coalesce(s.mobile, '')) like %:q%
                   or lower(coalesce(s.fatherOrHusbandName, '')) like %:q%
                   or lower(coalesce(s.area, '')) like %:q%
                   or lower(coalesce(s.centerPoint, '')) like %:q%
                   or lower(coalesce(s.department, '')) like %:q%)
            """)
    Page<Sewadar> search(@Param("q") String q,
                         @Param("zoneId") Long zoneId,
                         @Param("active") Boolean active,
                         @Param("zoneIds") Collection<Long> zoneIds,
                         @Param("sewadarId") Long sewadarId,
                         Pageable pageable);

    @Query("""
            select s from Sewadar s
            where s.active = true
              and (:zoneIds is null or s.zone.id in :zoneIds)
              and (:zoneId is null or s.zone.id = :zoneId)
            order by s.name asc
            """)
    List<Sewadar> findForAttendanceSheet(@Param("zoneId") Long zoneId,
                                         @Param("zoneIds") Collection<Long> zoneIds);

    /**
     * Attendance screen lookup: badge number, name, mobile or Aadhaar in one query.
     *
     * <p>Badge and Aadhaar are matched exactly (Aadhaar as bare digits, which is how
     * it is stored) so scanning a card lands on one person; name and mobile are
     * matched as a contains, which is how people actually search. Always narrowed by
     * the caller's zone scope.</p>
     */
    @Query("""
            select s from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:sewadarId is null or s.id = :sewadarId)
              and (
                    lower(s.badgeNumber) = :exact
                 or s.aadharNumber = :digits
                 or lower(s.name) like %:q%
                 or replace(replace(coalesce(s.mobile, ''), ' ', ''), '-', '') like %:digits%
              )
            order by s.active desc, s.name asc
            """)
    List<Sewadar> lookup(@Param("q") String q,
                         @Param("exact") String exact,
                         @Param("digits") String digits,
                         @Param("zoneIds") Collection<Long> zoneIds,
                         @Param("sewadarId") Long sewadarId,
                         Pageable pageable);
}

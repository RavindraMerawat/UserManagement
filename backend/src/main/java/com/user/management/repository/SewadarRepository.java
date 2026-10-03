package com.user.management.repository;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Gender;
import com.user.management.entity.Locality;
import com.user.management.repository.projection.GenderCount;
import com.user.management.repository.projection.LocalityGenderCount;
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

    /**
     * The sewadar records behind a page of accounts, in one query.
     *
     * <p>An account shows the photo of the sewadar it belongs to rather than keeping
     * a second copy of it, so a page of accounts needs its people - and asking once
     * per row would be a query per account for one column.</p>
     */
    List<Sewadar> findByUserIdIn(Collection<Long> userIds);

    /**
     * The same lookup by GR. No, for accounts that carry a number but no link.
     *
     * <p>An account is linked to its sewadar by {@code sewadars.user_id}, and the
     * accounts made before that link existed do not have one - several of them carry
     * the GR. No the office typed and nothing else. The number identifies the person
     * just as well, so it is the second way of finding them.</p>
     */
    List<Sewadar> findByBadgeNumberIgnoreCaseIn(Collection<String> badgeNumbers);

    long count();

    long countByZoneIdIn(Collection<Long> zoneIds);

    @Query("""
            select count(s) from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:issued is null or s.badgeIssued = :issued)
              and (:received is null or s.badgeReceived = :received)
            """)
    long countBadges(@Param("zoneIds") Collection<Long> zoneIds,
                     @Param("scopeGender") Gender scopeGender,
                     @Param("sewadarId") Long sewadarId,
                     @Param("issued") Boolean issued,
                     @Param("received") Boolean received);

    /** Sewadars in scope. Every record counts - there is no active flag any more. */
    @Query("""
            select count(s) from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarId is null or s.id = :sewadarId)
            """)
    long countInScope(@Param("zoneIds") Collection<Long> zoneIds,
                      @Param("scopeGender") Gender scopeGender,
                      @Param("sewadarId") Long sewadarId);

    /**
     * Sewadars that already existed on a given date, from the audit stamp.
     *
     * <p>Used for the "vs last month" figure on the Total tile. There is no history
     * table, so this is the honest approximation available: rows created on or before
     * that date.
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
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
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
                                                @Param("scopeGender") Gender scopeGender,
                                                @Param("sewadarId") Long sewadarId);

    /**
     * The register as the office reads it: local and outstation, men and women.
     *
     * <p>One pass, grouped twice, for the four numbers the dashboard shows. Rows
     * with no locality or no gender recorded come back under a null key so they can
     * be shown as what they are rather than counted into a bucket they are not
     * in.</p>
     */
    @Query("""
            select s.locality as locality, s.gender as gender, count(s) as count
            from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarId is null or s.id = :sewadarId)
            group by s.locality, s.gender
            """)
    List<LocalityGenderCount> countByLocalityAndGender(@Param("zoneIds") Collection<Long> zoneIds,
                                                       @Param("scopeGender") Gender scopeGender,
                                                       @Param("sewadarId") Long sewadarId);

    @Query("""
            select count(s) from Sewadar s
            where s.createdAt <= :asOf
              and (:zoneIds is null or s.zone.id in :zoneIds)
                and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarId is null or s.id = :sewadarId)
            """)
    long countActiveAsOf(@Param("asOf") Instant asOf,
                         @Param("zoneIds") Collection<Long> zoneIds,
                         @Param("scopeGender") Gender scopeGender,
                         @Param("sewadarId") Long sewadarId);

    /*
     * The dashboard pair. A tile and the grid behind it must never disagree, so the
     * count and the list are the same WHERE clause written twice - change one and you
     * have to change the other, which is visible, rather than two different
     * definitions drifting apart quietly.
     *
     * `exists` rather than a join: attendance is one row per sewadar per date per
     * sewa type, so somebody marked present for both Daily and Construction sewa on
     * one day would otherwise be counted, and listed, twice. These count people.
     *
     * A null status means every sewadar in scope, which is the Total tile.
     */

    @Query("""
            select s from Sewadar s
            join s.zone z
            where (:zoneIds is null or z.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:gender is null or s.gender = :gender)
              and (:locality is null or s.locality = :locality)
              and (:status is null or exists (
                    select a.id from Attendance a
                    where a.sewadar = s
                      and a.attendanceDate = :onDate
                      and a.status = :status))
            """)
    Page<Sewadar> findForDashboard(@Param("status") AttendanceStatus status,
                                   @Param("onDate") LocalDate onDate,
                                   @Param("zoneIds") Collection<Long> zoneIds,
                                   @Param("scopeGender") Gender scopeGender,
                                   @Param("sewadarId") Long sewadarId,
                                   @Param("gender") Gender gender,
                                   @Param("locality") Locality locality,
                                   Pageable pageable);

    @Query("""
            select count(s) from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
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
                           @Param("scopeGender") Gender scopeGender,
                           @Param("sewadarId") Long sewadarId);

    /**
     * Search restricted by an optional zone id list. A null {@code zoneIds} means no
     * zone restriction (global-scope roles); a null {@code sewadarId} means no
     * self-only restriction.
     *
     * <p>The designation is a <b>left</b> join: a sewadar whose designation has never
     * been filled in still appears in an unfiltered search, and only drops out when a
     * designation is actually asked for. Written as {@code s.role.id} it would have
     * been an inner join and those records would have vanished from the screen.</p>
     */
    @Query("""
            select s from Sewadar s
            join s.zone z
            left join s.role rl
            where (:zoneIds is null or z.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarId is null or s.id = :sewadarId)
              and (:zoneId is null or z.id = :zoneId)
              and (:designationId is null or rl.id = :designationId)
              and (:badgeIssued is null or s.badgeIssued = :badgeIssued)
              and (:badgeReceived is null or s.badgeReceived = :badgeReceived)
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
                         @Param("designationId") Long designationId,
                         @Param("badgeIssued") Boolean badgeIssued,
                         @Param("badgeReceived") Boolean badgeReceived,
                         @Param("zoneIds") Collection<Long> zoneIds,
                         @Param("scopeGender") Gender scopeGender,
                         @Param("sewadarId") Long sewadarId,
                         Pageable pageable);

    @Query("""
            select s from Sewadar s
            where (:zoneIds is null or s.zone.id in :zoneIds)
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:zoneId is null or s.zone.id = :zoneId)
            order by s.name asc
            """)
    List<Sewadar> findForAttendanceSheet(@Param("zoneId") Long zoneId,
                                         @Param("zoneIds") Collection<Long> zoneIds,
                                         @Param("scopeGender") Gender scopeGender);

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
              and (:scopeGender is null or s.gender = :scopeGender)
              and (:sewadarId is null or s.id = :sewadarId)
              and (
                    lower(s.badgeNumber) = :exact
                 or s.aadharNumber = :digits
                 or lower(s.name) like %:q%
                 or replace(replace(coalesce(s.mobile, ''), ' ', ''), '-', '') like %:digits%
              )
            order by s.name asc
            """)
    List<Sewadar> lookup(@Param("q") String q,
                         @Param("exact") String exact,
                         @Param("digits") String digits,
                         @Param("zoneIds") Collection<Long> zoneIds,
                         @Param("scopeGender") Gender scopeGender,
                         @Param("sewadarId") Long sewadarId,
                         Pageable pageable);

    /** Used by Setup to decide whether a designation can be deleted or must retire. */
    long countByRoleId(Long roleId);

    long countBySewaPointId(Long sewaPointId);

    /**
     * Is any sewadar recorded in this area?
     *
     * <p>Areas are written onto the record by name rather than by id, so this is the
     * question Setup has to ask before removing one: delete a name that is in use and
     * those records point at something no list offers any more.</p>
     */
    boolean existsByAreaIgnoreCase(String area);

    /** The same question for a satsang point. */
    boolean existsByCenterPointIgnoreCase(String centerPoint);
}

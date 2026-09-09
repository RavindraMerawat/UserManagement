package com.user.management.repository;

import com.user.management.entity.Sewadar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SewadarRepository extends JpaRepository<Sewadar, Long> {

    Optional<Sewadar> findByBadgeNumberIgnoreCase(String badgeNumber);

    boolean existsByBadgeNumberIgnoreCase(String badgeNumber);

    /** Aadhaar numbers are stored as bare digits, so an exact match is enough. */
    boolean existsByAadharNumber(String aadharNumber);

    Optional<Sewadar> findByUserId(Long userId);

    long countByActiveTrue();

    long countByZoneIdInAndActiveTrue(Collection<Long> zoneIds);

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
}

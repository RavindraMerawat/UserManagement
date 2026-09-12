package com.user.management.repository;

import com.user.management.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AreaRepository extends JpaRepository<Area, Long> {

    /**
     * Areas the caller may see, newest zone rules applied: a null {@code zoneIds}
     * means every zone, and an explicit {@code zoneId} narrows to one.
     */
    @Query("""
            select a from Area a
            join a.zone z
            where (:zoneIds is null or z.id in :zoneIds)
              and (:zoneId is null or z.id = :zoneId)
              and (:active is null or a.active = :active)
            order by z.name asc, a.name asc
            """)
    List<Area> findInScope(@Param("zoneId") Long zoneId,
                           @Param("active") Boolean active,
                           @Param("zoneIds") Collection<Long> zoneIds);

    boolean existsByZoneIdAndNameIgnoreCase(Long zoneId, String name);

    long countByZoneIdAndActiveTrue(Long zoneId);
}

package com.user.management.repository;

import com.user.management.entity.SatsangPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SatsangPointRepository extends JpaRepository<SatsangPoint, Long> {

    /** Points the caller may see, narrowed by zone scope and optionally by area. */
    @Query("""
            select p from SatsangPoint p
            join p.area a
            join a.zone z
            where (:zoneIds is null or z.id in :zoneIds)
              and (:areaId is null or a.id = :areaId)
              and (:zoneId is null or z.id = :zoneId)
              and (:active is null or p.active = :active)
            order by z.name asc, a.name asc, p.name asc
            """)
    List<SatsangPoint> findInScope(@Param("areaId") Long areaId,
                                   @Param("zoneId") Long zoneId,
                                   @Param("active") Boolean active,
                                   @Param("zoneIds") Collection<Long> zoneIds);

    boolean existsByAreaIdAndNameIgnoreCase(Long areaId, String name);

    long countByAreaIdAndActiveTrue(Long areaId);
}

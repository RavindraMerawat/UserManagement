package com.user.management.repository;

import com.user.management.entity.SatsangPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SatsangPointRepository extends JpaRepository<SatsangPoint, Long> {

    /** Every satsang point, or only the live ones. */
    @Query("""
            select p from SatsangPoint p
            where (:active is null or p.active = :active)
            order by p.name asc
            """)
    List<SatsangPoint> findAllInOrder(@Param("active") Boolean active);

    boolean existsByNameIgnoreCase(String name);
}

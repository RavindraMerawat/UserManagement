package com.user.management.repository;

import com.user.management.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AreaRepository extends JpaRepository<Area, Long> {

    /**
     * Every area, or only the live ones.
     *
     * <p>No zone narrowing: an area belongs to no zone, so there is nothing to
     * narrow it by and everyone who can open the form sees the same list.</p>
     */
    @Query("""
            select a from Area a
            where (:active is null or a.active = :active)
            order by a.name asc
            """)
    List<Area> findAllInOrder(@Param("active") Boolean active);

    boolean existsByNameIgnoreCase(String name);
}

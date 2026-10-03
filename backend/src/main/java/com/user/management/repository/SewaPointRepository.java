package com.user.management.repository;

import com.user.management.entity.SewaPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SewaPointRepository extends JpaRepository<SewaPoint, Long> {

    List<SewaPoint> findAllByOrderByNameAsc();

    List<SewaPoint> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}

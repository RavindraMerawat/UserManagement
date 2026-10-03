package com.user.management.repository;

import com.user.management.entity.ConstructionSewa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ConstructionSewaRepository extends JpaRepository<ConstructionSewa, Long> {

    /** One sewadar's days, newest first. The screen only ever shows one sewadar. */
    Page<ConstructionSewa> findBySewadarIdOrderBySewaDateDesc(Long sewadarId, Pageable pageable);

    /** The rule the office asked for: a day is recorded once or not at all. */
    boolean existsBySewadarIdAndSewaDate(Long sewadarId, LocalDate sewaDate);

    long countBySewadarId(Long sewadarId);
}

package com.user.management.repository;

import com.user.management.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Long> {

    /** Two zones must not share a name, any more than they share a code. */
    boolean existsByNameIgnoreCase(String name);

    Optional<Zone> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Zone> findAllByActiveTrueOrderByNameAsc();

    List<Zone> findAllByOrderByNameAsc();
}

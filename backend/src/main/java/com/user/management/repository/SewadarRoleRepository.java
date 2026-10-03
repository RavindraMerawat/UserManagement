package com.user.management.repository;

import com.user.management.entity.SewadarRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** The roles table - shown on the screens as Designation. */
public interface SewadarRoleRepository extends JpaRepository<SewadarRole, Long> {

    List<SewadarRole> findAllByOrderByNameAsc();

    List<SewadarRole> findByActiveTrueOrderByNameAsc();

    Optional<SewadarRole> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}

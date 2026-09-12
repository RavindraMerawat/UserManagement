package com.user.management.repository;

import com.user.management.entity.Role;
import com.user.management.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Email is optional on an account, but two accounts must not share one. */
    boolean existsByEmailIgnoreCase(String email);

    /** Active accounts, for the dashboard tile. */
    long countByEnabledTrue();

    /** Active accounts that already existed on a date, for its "vs last month". */
    long countByEnabledTrueAndCreatedAtLessThanEqual(Instant asOf);

    List<User> findAllByRole(Role role);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:enabled is null or u.enabled = :enabled)
              and (:q is null or lower(u.username) like %:q%
                   or lower(u.fullName) like %:q%
                   or lower(coalesce(u.email, '')) like %:q%)
            """)
    Page<User> search(@Param("q") String q, @Param("role") Role role,
                      @Param("enabled") Boolean enabled, Pageable pageable);

    /** Accounts, optionally narrowed to enabled or disabled, for the tabs. */
    @Query("select count(u) from User u where (:enabled is null or u.enabled = :enabled)")
    long countByStatus(@Param("enabled") Boolean enabled);

    @Query("select count(u) from User u where u.role = :role and u.enabled = true")
    long countEnabledByRole(@Param("role") Role role);
}

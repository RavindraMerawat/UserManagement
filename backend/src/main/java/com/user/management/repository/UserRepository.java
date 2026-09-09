package com.user.management.repository;

import com.user.management.entity.Role;
import com.user.management.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByRole(Role role);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:q is null or lower(u.username) like %:q%
                   or lower(u.fullName) like %:q%
                   or lower(coalesce(u.email, '')) like %:q%)
            """)
    Page<User> search(@Param("q") String q, @Param("role") Role role, Pageable pageable);

    @Query("select count(u) from User u where u.role = :role and u.enabled = true")
    long countEnabledByRole(@Param("role") Role role);
}

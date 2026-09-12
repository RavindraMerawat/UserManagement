package com.user.management.repository;

import com.user.management.entity.RequestStatus;
import com.user.management.entity.ZoneChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface ZoneChangeRequestRepository extends JpaRepository<ZoneChangeRequest, Long> {

    boolean existsBySewadarIdAndStatus(Long sewadarId, RequestStatus status);

    @Query("""
            select r from ZoneChangeRequest r
            where (:status is null or r.status = :status)
              and (:sewadarScopeId is null or r.sewadar.id = :sewadarScopeId)
              and (:zoneIds is null or r.fromZone.id in :zoneIds or r.toZone.id in :zoneIds)
            """)
    Page<ZoneChangeRequest> search(@Param("status") RequestStatus status,
                                   @Param("zoneIds") Collection<Long> zoneIds,
                                   @Param("sewadarScopeId") Long sewadarScopeId,
                                   Pageable pageable);

    /**
     * How many requests were sitting unreviewed at a moment in the past, for the
     * "vs last week" figure on the dashboard: raised by then, and either still
     * pending now or not reviewed until after then.
     */
    @Query("""
            select count(r) from ZoneChangeRequest r
            where r.createdAt <= :asOf
              and (r.status = com.user.management.entity.RequestStatus.PENDING
                   or r.reviewedAt > :asOf)
              and (:sewadarScopeId is null or r.sewadar.id = :sewadarScopeId)
              and (:zoneIds is null or r.fromZone.id in :zoneIds or r.toZone.id in :zoneIds)
            """)
    long countPendingAsOf(@Param("asOf") Instant asOf,
                          @Param("zoneIds") Collection<Long> zoneIds,
                          @Param("sewadarScopeId") Long sewadarScopeId);

    /** Requests in scope, optionally narrowed to one status, for the tab strip. */
    @Query("""
            select count(r) from ZoneChangeRequest r
            where (:status is null or r.status = :status)
              and (:sewadarScopeId is null or r.sewadar.id = :sewadarScopeId)
              and (:zoneIds is null or r.fromZone.id in :zoneIds or r.toZone.id in :zoneIds)
            """)
    long countByStatusInScope(@Param("status") RequestStatus status,
                              @Param("zoneIds") Collection<Long> zoneIds,
                              @Param("sewadarScopeId") Long sewadarScopeId);

    @Query("""
            select count(r) from ZoneChangeRequest r
            where r.status = :status
              and (:sewadarScopeId is null or r.sewadar.id = :sewadarScopeId)
              and (:zoneIds is null or r.fromZone.id in :zoneIds or r.toZone.id in :zoneIds)
            """)
    long countInScope(@Param("status") RequestStatus status,
                      @Param("zoneIds") Collection<Long> zoneIds,
                      @Param("sewadarScopeId") Long sewadarScopeId);
}

package com.user.management.repository;

import com.user.management.entity.Gender;
import com.user.management.entity.WeeklySeatingSewa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WeeklySeatingSewaRepository extends JpaRepository<WeeklySeatingSewa, Long> {

    /** One sewadar's seating days, newest first. */
    Page<WeeklySeatingSewa> findBySewadarIdOrderBySewaDateDesc(Long sewadarId, Pageable pageable);

    boolean existsBySewadarIdAndSewaDate(Long sewadarId, LocalDate sewaDate);

    /** Who already holds that token on that day, so the clash can be named. */
    Optional<WeeklySeatingSewa> findBySewaDateAndTokenNoIgnoreCase(LocalDate sewaDate, String tokenNo);

    Optional<WeeklySeatingSewa> findBySewadarIdAndSewaDate(Long sewadarId, LocalDate sewaDate);

    /**
     * The day's badge movements, counted by gender.
     *
     * <p>One query rather than four: the four numbers on the cards are one question
     * about one day, and four round trips would let them disagree with each other.</p>
     */
    @Query("""
            select
              sum(case when w.badgeIssued = true and s.gender = com.user.management.entity.Gender.MALE then 1 else 0 end),
              sum(case when w.badgeIssued = true and s.gender = com.user.management.entity.Gender.FEMALE then 1 else 0 end),
              sum(case when w.badgeReceived = true and s.gender = com.user.management.entity.Gender.MALE then 1 else 0 end),
              sum(case when w.badgeReceived = true and s.gender = com.user.management.entity.Gender.FEMALE then 1 else 0 end)
            from WeeklySeatingSewa w join w.sewadar s
            where w.sewaDate = :date
              and (:zoneIds is null or s.zone.id in :zoneIds)
                and (:scopeGender is null or s.gender = :scopeGender)
            """)
    List<Object[]> summariseDay(@Param("date") LocalDate date,
                                @Param("zoneIds") Collection<Long> zoneIds,
                                @Param("scopeGender") Gender scopeGender);

    /** The people behind one of those counts. */
    @Query("""
            select w from WeeklySeatingSewa w join w.sewadar s
            where w.sewaDate = :date
              and (:issued = false or w.badgeIssued = true)
              and (:issued = true or w.badgeReceived = true)
              and (:gender is null or s.gender = :gender)
              and (:zoneIds is null or s.zone.id in :zoneIds)
                and (:scopeGender is null or s.gender = :scopeGender)
            order by s.name asc
            """)
    Page<WeeklySeatingSewa> findDayMovements(@Param("date") LocalDate date,
                                             @Param("issued") boolean issued,
                                             @Param("gender") Gender gender,
                                             @Param("zoneIds") Collection<Long> zoneIds,
                                             @Param("scopeGender") Gender scopeGender,
                                             Pageable pageable);
}

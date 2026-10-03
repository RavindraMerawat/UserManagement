package com.user.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One seating sewa: a sewadar, the day they sat, and the token they were given.
 *
 * <p>The token is what the screens call the Badge No. It is the number on the seating
 * badge handed out that morning and collected back, so it belongs to the occasion
 * rather than to the person - the same sewadar carries a different one next week.</p>
 *
 * <p>Two rules live in the constraints. A sewadar is seated once on a day, and a
 * token is given to one person on a day: both are things the office would otherwise
 * only discover by finding two people holding the same badge.</p>
 */
@Entity
@Table(name = "weeklySeatingSewa",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_weeklySeating_sewadar_date",
                        columnNames = {"sewadarId", "sewaDate"}),
                @UniqueConstraint(name = "uk_weeklySeating_token_date",
                        columnNames = {"sewaDate", "tokenNo"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklySeatingSewa extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sewadarId", nullable = false)
    private Sewadar sewadar;

    @Column(name = "sewaDate", nullable = false)
    private LocalDate sewaDate;

    /** Sunday or Thursday, and it must agree with the date. */
    @Enumerated(EnumType.STRING)
    @Column(name = "weekDay", nullable = false, length = 10)
    private WeekDay weekDay;

    /** Shown as "Badge No" on the screens; it is the seating token for that day. */
    @Column(name = "tokenNo", nullable = false, length = 40)
    private String tokenNo;

    /*
     * What happened to the badge on this day, which is not the same question as the
     * sewadar's own badge flags. Those say whether they hold the annual satsang
     * badge; these say whether this token went out and came back on this Sunday or
     * Thursday, which is what the day's counts are of.
     */
    @Column(name = "badgeIssued", nullable = false)
    private boolean badgeIssued;

    @Column(name = "badgeReceived", nullable = false)
    private boolean badgeReceived;

    /*
     * When each happened, to the minute. The audit columns say when the row was last
     * written, which after a receive is the receive - so the issue time would be
     * lost if it were not kept here.
     */
    @Column(name = "issuedAt")
    private LocalTime issuedAt;

    @Column(name = "receivedAt")
    private LocalTime receivedAt;
}

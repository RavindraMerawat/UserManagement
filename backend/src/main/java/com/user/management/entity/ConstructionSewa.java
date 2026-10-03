package com.user.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * One day of construction sewa by one sewadar.
 *
 * <p>A row per occasion rather than a tally: a day either had construction sewa on
 * it or it did not, so the count is something to derive rather than to type. The
 * unique constraint on (sewadar, date) is the rule itself - the same day cannot be
 * recorded twice, and nobody has to remember whether they already entered it.</p>
 *
 * <p>Separate from attendance on purpose. Attendance answers "was this person here
 * on this day"; this answers "did they do construction sewa on it", and the two are
 * recorded by different people at different times.</p>
 */
@Entity
@Table(name = "constructionSewa",
        uniqueConstraints = @UniqueConstraint(name = "uk_constructionSewa_sewadar_date",
                columnNames = {"sewadarId", "sewaDate"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConstructionSewa extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Many, not one. A OneToOne puts a unique index on the join column by itself,
     * which is exactly the old one-row-per-sewadar rule wearing a different hat -
     * and it refused the second day before the (sewadar, date) rule was ever
     * consulted. The tests caught it.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sewadarId", nullable = false)
    private Sewadar sewadar;

    /**
     * The day the sewa was done, as the office records it.
     *
     * <p>Not the row's own updatedAt: that is when someone typed it in, which is
     * often days later and is a different question from when the sewa happened.</p>
     */
    @Column(name = "sewaDate", nullable = false)
    private LocalDate sewaDate;

    /** Free text the office adds when a figure needs explaining. */
    @Column(length = 300)
    private String remarks;
}

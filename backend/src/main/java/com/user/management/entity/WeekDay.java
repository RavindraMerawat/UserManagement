package com.user.management.entity;

import java.time.DayOfWeek;

/**
 * The two days weekly seating sewa runs on.
 *
 * <p>An enum of two rather than {@link DayOfWeek}: the office only ever seats on a
 * Sunday or a Thursday, and a list of seven invites the other five to be chosen by
 * mistake.</p>
 */
public enum WeekDay {

    SUNDAY("Sunday", DayOfWeek.SUNDAY),
    THURSDAY("Thursday", DayOfWeek.THURSDAY);

    private final String displayName;
    private final DayOfWeek dayOfWeek;

    WeekDay(String displayName, DayOfWeek dayOfWeek) {
        this.displayName = displayName;
        this.dayOfWeek = dayOfWeek;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }
}

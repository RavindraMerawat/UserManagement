package com.user.management.entity;

/**
 * The kinds of sewa attendance is marked against.
 *
 * <p>Weekly Sewa is not chosen on a screen: it is what the seating desk writes when
 * a badge is handed over, so the day shows up in the register as the seating it
 * was. The other three are the office's own list.</p>
 */
public enum SewaType {
    DAILY_SEWA("Daily Sewa"),
    ROSTER_SEWA("Roster Sewa"),
    CONSTRUCTION_SEWA("Construction Sewa"),
    WEEKLY_SEWA("Weekly Sewa");

    private final String displayName;

    SewaType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

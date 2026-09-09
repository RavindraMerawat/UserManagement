package com.user.management.entity;

public enum AttendanceStatus {
    PRESENT("Present", 1.0),
    HALF_DAY("Half Day", 0.5),
    LEAVE("Leave", 0.0),
    ABSENT("Absent", 0.0);

    private final String displayName;
    private final double dayWeight;

    AttendanceStatus(String displayName, double dayWeight) {
        this.displayName = displayName;
        this.dayWeight = dayWeight;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Fraction of a sewa day this status counts for. */
    public double getDayWeight() {
        return dayWeight;
    }
}

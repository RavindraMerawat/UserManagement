package com.user.management.entity;

/** What the desk is doing with the seating badge: handing it out, or taking it back. */
public enum BadgeAction {
    ISSUE("Issued"),
    RECEIVE("Received");

    private final String displayName;

    BadgeAction(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

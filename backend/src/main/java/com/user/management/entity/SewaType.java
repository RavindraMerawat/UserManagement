package com.user.management.entity;

public enum SewaType {
    ROSTER_SEWA("Roster Sewa"),
    CONSTRUCTION_SEWA("Construction Sewa"),
    OFFICE_SEWA("Office Sewa"),
    OTHER("Other");

    private final String displayName;

    SewaType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

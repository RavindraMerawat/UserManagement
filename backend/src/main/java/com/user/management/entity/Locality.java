package com.user.management.entity;

/** Whether a sewadar comes from the town or travels in for the sewa. */
public enum Locality {

    LOCAL("Local"),
    OUTSTATION("Outstation");

    private final String displayName;

    Locality(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

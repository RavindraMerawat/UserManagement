package com.user.management.entity;

/**
 * Whether a sewadar holds a permanent place on the roster or an open one.
 *
 * <p>This replaced the old "primary sewa type" field on the sewadar form. Sewa type
 * still exists, but it belongs to an attendance row - what someone did on a given
 * day - not to the person.</p>
 */
public enum SewadarStatus {

    PERMANENT("Permanent"),
    OPEN("Open");

    private final String displayName;

    SewadarStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

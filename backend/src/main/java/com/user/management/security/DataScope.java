package com.user.management.security;

import java.util.Collection;
import java.util.Set;

/**
 * The slice of data the current user is allowed to see.
 *
 * <p>Both fields are passed straight into the repository queries, where {@code null}
 * means "no restriction":</p>
 * <ul>
 *   <li>{@code zoneIds == null} &mdash; every zone (ADMIN, OFFICE_ADMIN, OFFICE_USER)</li>
 *   <li>{@code zoneIds != null} &mdash; only these zones (ZONE_INCHARGE, SUPERVISOR)</li>
 *   <li>{@code sewadarId != null} &mdash; only this sewadar's own records (SEWADAR)</li>
 * </ul>
 *
 * @param zoneIds   accessible zone ids, or {@code null} for all zones
 * @param sewadarId the only sewadar id visible, or {@code null} for no such limit
 */
public record DataScope(Collection<Long> zoneIds, Long sewadarId) {

    public static DataScope global() {
        return new DataScope(null, null);
    }

    public static DataScope zones(Set<Long> zoneIds) {
        // An empty zone set would make "in ()" invalid, so fall back to an impossible id.
        return new DataScope(zoneIds.isEmpty() ? Set.of(-1L) : zoneIds, null);
    }

    public static DataScope selfOnly(Long sewadarId) {
        return new DataScope(null, sewadarId == null ? -1L : sewadarId);
    }

    public boolean isGlobal() {
        return zoneIds == null && sewadarId == null;
    }

    public boolean allowsZone(Long zoneId) {
        return zoneIds == null || (zoneId != null && zoneIds.contains(zoneId));
    }

    public boolean allowsSewadar(Long id) {
        return sewadarId == null || sewadarId.equals(id);
    }
}

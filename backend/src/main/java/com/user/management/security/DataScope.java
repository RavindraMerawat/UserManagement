package com.user.management.security;

import com.user.management.entity.Gender;

import java.util.Collection;
import java.util.Set;

/**
 * The slice of data the current user is allowed to see.
 *
 * <p>All three fields are passed straight into the repository queries, where
 * {@code null} means "no restriction":</p>
 * <ul>
 *   <li>{@code zoneIds == null} &mdash; every zone (ADMIN, OFFICE_ADMIN, OFFICE_USER)</li>
 *   <li>{@code zoneIds != null} &mdash; only these zones (ZONE_INCHARGE, SUPERVISOR)</li>
 *   <li>{@code sewadarId != null} &mdash; only this sewadar's own records (SEWADAR)</li>
 *   <li>{@code gender != null} &mdash; only sewadars of that gender</li>
 * </ul>
 *
 * <p>The gender comes from the account and narrows every role except Admin: a male
 * login reads the male register, a female login the female one. An account with no
 * gender set sees both, which is how every account behaved before the field existed
 * - the restriction starts applying to an account the moment somebody fills it in,
 * and never takes a working account away in the meantime.</p>
 *
 * @param zoneIds   accessible zone ids, or {@code null} for all zones
 * @param sewadarId the only sewadar id visible, or {@code null} for no such limit
 * @param gender    the only gender visible, or {@code null} for both
 */
public record DataScope(Collection<Long> zoneIds, Long sewadarId, Gender gender) {

    public static DataScope global() {
        return new DataScope(null, null, null);
    }

    public static DataScope zones(Set<Long> zoneIds) {
        // An empty zone set would make "in ()" invalid, so fall back to an impossible id.
        return new DataScope(zoneIds.isEmpty() ? Set.of(-1L) : zoneIds, null, null);
    }

    public static DataScope selfOnly(Long sewadarId) {
        return new DataScope(null, sewadarId == null ? -1L : sewadarId, null);
    }

    /** The same scope, narrowed to one gender. A null gender changes nothing. */
    public DataScope forGender(Gender only) {
        return only == null ? this : new DataScope(zoneIds, sewadarId, only);
    }

    public boolean isGlobal() {
        return zoneIds == null && sewadarId == null && gender == null;
    }

    public boolean allowsZone(Long zoneId) {
        return zoneIds == null || (zoneId != null && zoneIds.contains(zoneId));
    }

    public boolean allowsSewadar(Long id) {
        return sewadarId == null || sewadarId.equals(id);
    }

    public boolean allowsGender(Gender other) {
        return gender == null || gender == other;
    }
}

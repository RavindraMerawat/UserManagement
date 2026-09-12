package com.user.management.model;

import java.time.Instant;
import java.time.LocalTime;

/**
 * One search hit on the Mark Attendance screen: who they are, plus where they stand
 * today so the screen can enable Check In or Check Out.
 */
public record SewadarLookupResponse(
        Long sewadarId,
        String badgeNumber,
        String name,
        String fatherOrHusbandName,
        String mobile,

        /** Full 12 digits, or {@code XXXX XXXX 9012} when the caller may not see them. */
        String aadharNumber,

        /** True when {@code aadharNumber} is masked, so the card hides its reveal toggle. */
        boolean aadharMasked,

        String department,
        String zoneName,
        String area,
        String centerPoint,
        boolean active,
        boolean hasPhoto,
        Instant photoUpdatedAt,

        /** Today's entry, if one exists. */
        Long attendanceId,
        String sewaType,
        String sewaTypeLabel,
        String status,
        String statusLabel,
        LocalTime inTime,
        LocalTime outTime,
        Double hours,
        boolean checkedIn,
        boolean checkedOut
) {
}

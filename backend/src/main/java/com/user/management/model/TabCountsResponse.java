package com.user.management.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The numbers on a list screen's tab strip - "All (1,248)  Active (1,102)".
 *
 * <p>Deliberately a map rather than a fixed set of fields, because each screen has a
 * different set of tabs and they change: sewadars split by active, requests by
 * status. The keys are the tab names the UI renders, in the order they should be
 * shown, and {@code total} is the "All" tab.</p>
 *
 * <p>Every count is taken with the caller's data scope applied, exactly like the list
 * it heads - so a Zone Incharge's "All" is their zones, not the register.</p>
 */
public record TabCountsResponse(long total, Map<String, Long> byStatus) {

    /** Builds the counts in the order the tabs are drawn. */
    public static TabCountsResponse of(long total, Object... namesAndCounts) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i + 1 < namesAndCounts.length; i += 2) {
            counts.put((String) namesAndCounts[i], ((Number) namesAndCounts[i + 1]).longValue());
        }
        return new TabCountsResponse(total, counts);
    }
}

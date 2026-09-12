package com.user.management.model;

import java.util.List;

/**
 * Outcome of a bulk check in or check out. Sewadars that could not be marked are
 * reported per row rather than failing the whole batch, so one bad record does not
 * stop a zone sheet from being saved.
 */
public record CheckInOutResult(
        int requested,
        int succeeded,
        int skipped,
        List<AttendanceResponse> marked,
        List<Skipped> skippedRows
) {
    public record Skipped(Long sewadarId, String sewadarName, String reason) {
    }
}

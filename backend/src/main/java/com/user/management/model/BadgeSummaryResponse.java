package com.user.management.model;

/** Badge counts visible to the signed-in user's normal data scope. */
public record BadgeSummaryResponse(long issued, long received, long pending) {
}

package com.user.management.model;

import com.user.management.entity.RequestStatus;
import com.user.management.entity.ZoneChangeRequest;

import java.time.Instant;

public record ZoneChangeRequestResponse(
        Long id,
        Long sewadarId,
        String badgeNumber,
        String sewadarName,
        Long fromZoneId,
        String fromZoneName,
        Long toZoneId,
        String toZoneName,
        String reason,
        RequestStatus status,
        String statusLabel,
        String requestedBy,
        Instant requestedAt,
        String reviewedBy,
        Instant reviewedAt,
        String reviewRemarks
) {
    public static ZoneChangeRequestResponse from(ZoneChangeRequest r) {
        return new ZoneChangeRequestResponse(
                r.getId(),
                r.getSewadar().getId(),
                r.getSewadar().getBadgeNumber(),
                r.getSewadar().getName(),
                r.getFromZone().getId(),
                r.getFromZone().getName(),
                r.getToZone().getId(),
                r.getToZone().getName(),
                r.getReason(),
                r.getStatus(),
                r.getStatus().getDisplayName(),
                r.getRequestedBy(),
                r.getCreatedAt(),
                r.getReviewedBy(),
                r.getReviewedAt(),
                r.getReviewRemarks());
    }
}

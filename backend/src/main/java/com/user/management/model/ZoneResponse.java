package com.user.management.model;

import com.user.management.entity.Zone;

public record ZoneResponse(
        Long id,
        String code,
        String name,
        String description,
        String centre,
        boolean active
) {
    public static ZoneResponse from(Zone zone) {
        if (zone == null) {
            return null;
        }
        return new ZoneResponse(zone.getId(), zone.getCode(), zone.getName(),
                zone.getDescription(), zone.getCentre(), zone.isActive());
    }
}

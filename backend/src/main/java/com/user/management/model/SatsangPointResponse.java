package com.user.management.model;

import com.user.management.entity.SatsangPoint;

/** One satsang point, with its area and zone named for the list. */
public record SatsangPointResponse(
        Long id,
        String name,
        Long areaId,
        String areaName,
        Long zoneId,
        String zoneName,
        boolean active
) {
    public static SatsangPointResponse from(SatsangPoint point) {
        var area = point.getArea();
        var zone = area == null ? null : area.getZone();
        return new SatsangPointResponse(
                point.getId(),
                point.getName(),
                area == null ? null : area.getId(),
                area == null ? null : area.getName(),
                zone == null ? null : zone.getId(),
                zone == null ? null : zone.getName(),
                point.isActive());
    }
}

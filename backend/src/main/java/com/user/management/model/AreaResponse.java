package com.user.management.model;

import com.user.management.entity.Area;

/** One area, with the zone it belongs to named for the list. */
public record AreaResponse(Long id, String name, Long zoneId, String zoneName, boolean active) {

    public static AreaResponse from(Area area) {
        return new AreaResponse(
                area.getId(),
                area.getName(),
                area.getZone() == null ? null : area.getZone().getId(),
                area.getZone() == null ? null : area.getZone().getName(),
                area.isActive());
    }
}

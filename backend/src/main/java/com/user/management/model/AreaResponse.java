package com.user.management.model;

import com.user.management.entity.Area;

public record AreaResponse(Long id, String name, boolean active) {

    public static AreaResponse from(Area area) {
        return new AreaResponse(area.getId(), area.getName(), area.isActive());
    }
}

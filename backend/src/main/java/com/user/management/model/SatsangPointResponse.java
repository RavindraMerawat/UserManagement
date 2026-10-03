package com.user.management.model;

import com.user.management.entity.SatsangPoint;

public record SatsangPointResponse(Long id, String name, boolean active) {

    public static SatsangPointResponse from(SatsangPoint point) {
        return new SatsangPointResponse(point.getId(), point.getName(), point.isActive());
    }
}

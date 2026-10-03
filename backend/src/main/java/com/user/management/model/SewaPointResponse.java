package com.user.management.model;

import com.user.management.entity.SewaPoint;

/** One sewa point, for the Setup list and the sewadar form's dropdown. */
public record SewaPointResponse(Long id, String name, boolean active) {

    public static SewaPointResponse from(SewaPoint p) {
        return new SewaPointResponse(p.getId(), p.getName(), p.isActive());
    }
}

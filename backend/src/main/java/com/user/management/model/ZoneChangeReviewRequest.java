package com.user.management.model;

import com.user.management.entity.RequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ZoneChangeReviewRequest(
        @NotNull(message = "Decision is required") RequestStatus decision,
        @Size(max = 500) String remarks
) {
}

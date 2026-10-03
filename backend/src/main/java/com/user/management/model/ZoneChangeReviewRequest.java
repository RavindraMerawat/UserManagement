package com.user.management.model;

import com.user.management.entity.RequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A decision on a zone change request.
 *
 * <p>The reason is required whichever way it goes. An approval with no reason is
 * as unhelpful as a rejection with none when someone reads the record months
 * later and asks why the sewadar moved.</p>
 */
public record ZoneChangeReviewRequest(
        @NotNull(message = "Decision is required") RequestStatus decision,

        @NotBlank(message = "A reason is required, for an approval as well as a rejection")
        @Size(max = 500, message = "Keep the reason under 500 characters")
        String remarks
) {
}

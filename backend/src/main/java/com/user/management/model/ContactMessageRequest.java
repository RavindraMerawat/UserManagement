package com.user.management.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Message raised from the Contact screen; delivered to the office admins. */
public record ContactMessageRequest(
        @NotBlank @Size(max = 150) String name,
        @Email @NotBlank @Size(max = 150) String email,
        @Size(max = 20) String mobile,
        @NotBlank @Size(max = 150) String subject,
        @NotBlank @Size(max = 2000) String message
) {
}

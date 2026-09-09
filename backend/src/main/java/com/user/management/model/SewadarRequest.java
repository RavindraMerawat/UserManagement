package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.SewaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Add or edit a sewadar. The first block matches the registration form field for
 * field; the second carries the optional details used by the reports.
 */
public record SewadarRequest(

        @NotBlank
        @Size(max = 40)
        @Schema(description = "Unique sewadar id", example = "SWD-1001")
        String badgeNumber,

        // ---- registration form fields ----

        @NotBlank(message = "Name is required")
        @Size(max = 150)
        @Schema(description = "Name", example = "Harpreet Singh")
        String name,

        @Size(max = 150)
        @Schema(description = "F/H Name - father or husband name", example = "Gurdeep Singh")
        String fatherOrHusbandName,

        @Schema(description = "Birth Date", example = "1990-04-18")
        LocalDate dateOfBirth,

        @Pattern(regexp = "^$|^[0-9+ -]{7,20}$", message = "Enter a valid mobile number")
        @Schema(description = "Mobile No", example = "9876543210")
        String mobile,

        @NotNull(message = "Zone is required")
        @Schema(description = "Zone id")
        Long zoneId,

        @Size(max = 400)
        @Schema(description = "Address")
        String address,

        /*
         * Accepts the digits with or without the usual grouping spaces or dashes; the
         * service strips them and enforces the 12 digit rule, so a formatted value
         * pasted from a card is not rejected here before it can be normalised.
         */
        @Pattern(regexp = "^$|^[0-9][0-9 -]{10,16}[0-9]$",
                message = "Aadhaar number must be 12 digits")
        @Schema(description = "Aadhaar No, 12 digits. Spaces and dashes are ignored.",
                example = "1234 5678 9012")
        String aadharNumber,

        @Size(max = 10)
        @Schema(description = "Blood Group", example = "O+")
        String bloodGroup,

        @Size(max = 120)
        @Schema(description = "Area inside the zone", example = "Sector 12")
        String area,

        @Size(max = 120)
        @Schema(description = "Center / Point the sewadar reports to", example = "Main Center")
        String centerPoint,

        // ---- additional details ----

        Gender gender,

        @Email @Size(max = 150) String email,

        @Size(max = 80) String city,

        @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be 6 digits") String pincode,

        @Size(max = 120) String department,

        SewaType primarySewaType,

        LocalDate joiningDate,

        Boolean active,

        @Schema(description = "Create a SEWADAR login for this sewadar")
        Boolean createLogin,

        @Size(max = 60)
        @Schema(description = "Username for the new login; defaults to the badge number")
        String loginUsername
) {
}

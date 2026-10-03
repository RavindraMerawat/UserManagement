package com.user.management.model;

import com.user.management.entity.Gender;
import com.user.management.entity.Locality;
import com.user.management.entity.SewadarStatus;
import com.user.management.entity.SewaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Add or edit a sewadar. The first block matches the registration form field for
 * field; the second carries the optional details used by the reports.
 */
public record SewadarRequest(

        @NotBlank
        @Size(max = 40)
        @Schema(description = "Unique sewadar id", example = "SWD-1001")
        String badgeNumber,

        @Schema(description = "Whether the issued badge has been received by the sewadar")
        Boolean badgeReceived,

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

        @Min(value = 1, message = "Age must be between 1 and 120")
        @Max(value = 120, message = "Age must be between 1 and 120")
        @Schema(description = "Age in years", example = "34")
        Integer age,

        /*
         * Exactly ten digits. Spaces and dashes are stripped by the service before
         * this is checked, so "98765 43210" is accepted and stored as ten digits -
         * the rule is on the number, not on how it was typed.
         */
        @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be 10 digits")
        @Schema(description = "Mobile No, 10 digits", example = "9876543210")
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

        @Schema(description = "Other zones a co-ordinator covers, beyond their own")
        List<Long> extraZoneIds,

        @Size(max = 120)
        @Schema(description = "Area inside the zone", example = "Sector 12")
        String area,

        @Size(max = 120)
        @Schema(description = "Grouping inside the area. Only Indore's areas use it.")
        String grouping,

        @NotNull(message = "Locality is required")
        @Schema(description = "LOCAL or OUTSTATION")
        Locality locality,

        @Size(max = 120)
        @Schema(description = "Center / Point the sewadar reports to", example = "Main Center")
        String centerPoint,

        // ---- additional details ----

        Gender gender,

        @Email @Size(max = 150) String email,



        @Size(max = 120) String department,

        @Schema(description = "PERMANENT or OPEN")
        SewadarStatus status,

        @Schema(description = "Id from the designations list")
        Long designationId,

        @Schema(description = "Id from the sewa points list")
        Long sewaPointId,

        LocalDate joiningDate,

        @Schema(description = "Excused from attendance. Defaults to false.")
        Boolean exempted,

        @Schema(description = "Create a SEWADAR login for this sewadar")
        Boolean createLogin,

        @Size(max = 60)
        @Schema(description = "Username for the new login; defaults to the badge number")
        String loginUsername
) {
}

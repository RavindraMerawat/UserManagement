package com.user.management.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Shares a generated report over email and/or WhatsApp. At least one channel must
 * be selected and carry a recipient.
 */
public record ShareReportRequest(
        @Schema(description = "Email recipients") @Size(max = 20) List<String> emailTo,
        @Schema(description = "WhatsApp recipients in international format, e.g. 919876543210")
        @Size(max = 20) List<String> whatsappTo,
        @Schema(description = "Overrides the default subject line") String subject,
        @Schema(description = "Extra note prepended to the message body") String note,
        @Schema(description = "Attach the report as an Excel workbook to the email", defaultValue = "true")
        Boolean attachExcel
) {
}

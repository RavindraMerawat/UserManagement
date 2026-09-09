package com.user.management.model;

import java.util.List;

public record ShareResult(
        boolean emailSent,
        List<String> emailRecipients,
        boolean whatsappSent,
        List<String> whatsappRecipients,
        List<String> warnings
) {
}

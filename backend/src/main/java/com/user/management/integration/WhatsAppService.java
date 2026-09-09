package com.user.management.integration;

import com.user.management.config.AppProperties;
import com.user.management.exception.IntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Sends WhatsApp messages through the Meta WhatsApp Cloud API.
 *
 * <p>Disabled by default. To switch it on set:</p>
 * <pre>
 * app.notification.whatsapp.enabled=true
 * app.notification.whatsapp.phone-number-id=&lt;your phone number id&gt;
 * app.notification.whatsapp.access-token=&lt;permanent access token&gt;
 * </pre>
 *
 * <p>Note the Cloud API rule: a free-form text message only reaches a number that
 * messaged your business in the last 24 hours. Outside that window you must send an
 * approved template, which {@link #sendTemplate} covers.</p>
 */
@Slf4j
@Service
public class WhatsAppService {

    private final AppProperties properties;
    private final RestClient restClient;

    public WhatsAppService(AppProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public boolean isEnabled() {
        AppProperties.WhatsApp cfg = properties.getNotification().getWhatsapp();
        return cfg.isEnabled()
                && cfg.getPhoneNumberId() != null && !cfg.getPhoneNumberId().isBlank()
                && cfg.getAccessToken() != null && !cfg.getAccessToken().isBlank();
    }

    /** Sends the same plain-text message to each recipient. */
    public void sendText(List<String> recipients, String message) {
        List<String> numbers = normalise(recipients);
        if (numbers.isEmpty()) {
            throw new IntegrationException("No valid WhatsApp recipient was supplied");
        }
        if (!isEnabled()) {
            log.warn("WhatsApp is disabled or not configured. Would have sent a {} character "
                    + "message to {}", message.length(), numbers);
            return;
        }
        for (String number : numbers) {
            post(Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", number,
                    "type", "text",
                    "text", Map.of("preview_url", false, "body", truncate(message))));
        }
    }

    /**
     * Sends an approved template message, which is what you need outside the 24 hour
     * customer service window.
     *
     * @param templateName name of the approved template
     * @param languageCode e.g. {@code en} or {@code en_US}
     * @param bodyParams   ordered values for the template body placeholders
     */
    public void sendTemplate(List<String> recipients,
                             String templateName,
                             String languageCode,
                             List<String> bodyParams) {
        List<String> numbers = normalise(recipients);
        if (numbers.isEmpty()) {
            throw new IntegrationException("No valid WhatsApp recipient was supplied");
        }
        if (!isEnabled()) {
            log.warn("WhatsApp is disabled or not configured. Would have sent template {} to {}",
                    templateName, numbers);
            return;
        }

        List<Map<String, String>> parameters = bodyParams == null
                ? List.of()
                : bodyParams.stream().map(v -> Map.of("type", "text", "text", v)).toList();

        for (String number : numbers) {
            post(Map.of(
                    "messaging_product", "whatsapp",
                    "to", number,
                    "type", "template",
                    "template", Map.of(
                            "name", templateName,
                            "language", Map.of("code", languageCode),
                            "components", List.of(Map.of(
                                    "type", "body",
                                    "parameters", parameters)))));
        }
    }

    private void post(Map<String, Object> payload) {
        AppProperties.WhatsApp cfg = properties.getNotification().getWhatsapp();
        String url = cfg.getApiUrl() + "/" + cfg.getPhoneNumberId() + "/messages";
        try {
            restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + cfg.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp message dispatched to {}", payload.get("to"));
        } catch (RestClientException e) {
            throw new IntegrationException("WhatsApp send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Strips spaces and punctuation and prefixes the configured country code when the
     * number looks like a local 10 digit one.
     */
    private List<String> normalise(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        String countryCode = properties.getNotification().getWhatsapp().getDefaultCountryCode();
        return recipients.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.replaceAll("[^0-9]", ""))
                .filter(n -> n.length() >= 10)
                .map(n -> n.length() == 10 ? countryCode + n : n)
                .distinct()
                .toList();
    }

    /** The Cloud API caps a text body at 4096 characters. */
    private String truncate(String message) {
        int limit = 4000;
        return message.length() <= limit
                ? message
                : message.substring(0, limit) + "\n... (truncated)";
    }
}

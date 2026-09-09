package com.user.management.integration;

import com.user.management.entity.Role;
import com.user.management.entity.User;
import com.user.management.entity.ZoneChangeRequest;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.IntegrationException;
import com.user.management.model.ContactMessageRequest;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.ShareReportRequest;
import com.user.management.model.ShareResult;
import com.user.management.report.ReportExporter;
import com.user.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fans a report or an event out over email and WhatsApp. A failure on one channel is
 * collected as a warning rather than failing the whole request, so a working channel
 * still delivers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final ReportExporter exporter;
    private final UserRepository userRepository;

    /** Shares a generated report on the channels named in the request. */
    public ShareResult shareReport(MonthlyReportResponse report, ShareReportRequest request) {
        boolean hasEmail = request.emailTo() != null && !request.emailTo().isEmpty();
        boolean hasWhatsApp = request.whatsappTo() != null && !request.whatsappTo().isEmpty();

        if (!hasEmail && !hasWhatsApp) {
            throw new BadRequestException("Add at least one email address or WhatsApp number");
        }

        List<String> warnings = new ArrayList<>();
        // "sent" means it actually left the server. A disabled channel is accepted and
        // logged, which is reported as not sent plus a warning saying why.
        boolean emailSent = false;
        boolean whatsappSent = false;
        boolean emailAccepted = false;
        boolean whatsappAccepted = false;

        if (hasEmail) {
            try {
                boolean attach = request.attachExcel() == null || request.attachExcel();
                String subject = request.subject() == null || request.subject().isBlank()
                        ? report.title()
                        : request.subject();
                emailService.send(
                        request.emailTo(),
                        subject,
                        exporter.toHtml(report, request.note()),
                        attach ? exporter.toExcel(report) : null,
                        attach ? exporter.fileName(report, "xlsx") : null,
                        attach ? XLSX_MIME : null);
                emailAccepted = true;
                emailSent = emailService.isEnabled();
                if (!emailSent) {
                    warnings.add("Email delivery is turned off on the server "
                            + "(app.notification.email.enabled), so the message was only logged.");
                }
            } catch (IntegrationException e) {
                log.error("Report email failed", e);
                warnings.add("Email: " + e.getMessage());
            }
        }

        if (hasWhatsApp) {
            try {
                whatsAppService.sendText(request.whatsappTo(),
                        exporter.toWhatsAppText(report, request.note()));
                whatsappAccepted = true;
                whatsappSent = whatsAppService.isEnabled();
                if (!whatsappSent) {
                    warnings.add("WhatsApp delivery is turned off or not configured on the server "
                            + "(app.notification.whatsapp.*), so the message was only logged.");
                }
            } catch (IntegrationException e) {
                log.error("Report WhatsApp share failed", e);
                warnings.add("WhatsApp: " + e.getMessage());
            }
        }

        if (!emailAccepted && !whatsappAccepted) {
            throw new IntegrationException("The report could not be shared: " + String.join(" ", warnings));
        }

        return new ShareResult(
                emailSent,
                emailAccepted ? request.emailTo() : List.of(),
                whatsappSent,
                whatsappAccepted ? request.whatsappTo() : List.of(),
                warnings);
    }

    /** Tells the office admins that a zone change was raised. */
    public void notifyZoneChangeRaised(ZoneChangeRequest request) {
        String subject = "Zone change request - " + request.getSewadar().getName();
        String body = """
                <div style="font-family:Segoe UI,Arial,sans-serif">
                  <h3>New zone change request</h3>
                  <p><b>Sewadar:</b> %s (%s)<br/>
                     <b>From zone:</b> %s<br/>
                     <b>To zone:</b> %s<br/>
                     <b>Reason:</b> %s<br/>
                     <b>Raised by:</b> %s</p>
                  <p>Please review it on the Request screen.</p>
                </div>
                """.formatted(
                request.getSewadar().getName(),
                request.getSewadar().getBadgeNumber(),
                request.getFromZone().getName(),
                request.getToZone().getName(),
                request.getReason() == null ? "-" : request.getReason(),
                request.getRequestedBy());

        quietly(() -> emailService.sendHtml(adminEmails(), subject, body),
                "zone change raised notification");
    }

    /** Tells the sewadar (and the raiser) what the decision was. */
    public void notifyZoneChangeReviewed(ZoneChangeRequest request) {
        String decision = request.getStatus().getDisplayName();
        String subject = "Zone change " + decision.toLowerCase() + " - " + request.getSewadar().getName();
        String body = """
                <div style="font-family:Segoe UI,Arial,sans-serif">
                  <h3>Zone change request %s</h3>
                  <p><b>Sewadar:</b> %s (%s)<br/>
                     <b>From zone:</b> %s<br/>
                     <b>To zone:</b> %s<br/>
                     <b>Reviewed by:</b> %s<br/>
                     <b>Remarks:</b> %s</p>
                </div>
                """.formatted(
                decision.toLowerCase(),
                request.getSewadar().getName(),
                request.getSewadar().getBadgeNumber(),
                request.getFromZone().getName(),
                request.getToZone().getName(),
                request.getReviewedBy(),
                request.getReviewRemarks() == null ? "-" : request.getReviewRemarks());

        List<String> recipients = new ArrayList<>();
        if (request.getSewadar().getEmail() != null) {
            recipients.add(request.getSewadar().getEmail());
        }
        recipients.addAll(adminEmails());

        quietly(() -> emailService.sendHtml(recipients, subject, body), "zone change decision notification");

        String mobile = request.getSewadar().getMobile();
        if (mobile != null && !mobile.isBlank()) {
            String text = """
                    *Zone change %s*
                    Sewadar: %s (%s)
                    From: %s
                    To: %s
                    Remarks: %s
                    """.formatted(
                    decision.toLowerCase(),
                    request.getSewadar().getName(),
                    request.getSewadar().getBadgeNumber(),
                    request.getFromZone().getName(),
                    request.getToZone().getName(),
                    request.getReviewRemarks() == null ? "-" : request.getReviewRemarks());
            quietly(() -> whatsAppService.sendText(List.of(mobile), text), "zone change WhatsApp");
        }
    }

    /** Delivers a Contact screen message to the office admins. */
    public void sendContactMessage(ContactMessageRequest request) {
        String subject = "[Contact] " + request.subject();
        String body = """
                <div style="font-family:Segoe UI,Arial,sans-serif">
                  <h3>Message from the Contact screen</h3>
                  <p><b>Name:</b> %s<br/>
                     <b>Email:</b> %s<br/>
                     <b>Mobile:</b> %s</p>
                  <p style="padding:10px 12px;background:#f3f4f6;border-radius:6px;white-space:pre-wrap">%s</p>
                </div>
                """.formatted(
                escape(request.name()),
                escape(request.email()),
                request.mobile() == null ? "-" : escape(request.mobile()),
                escape(request.message()));

        List<String> admins = adminEmails();
        if (admins.isEmpty()) {
            log.warn("Contact message received but no admin has an email address on file");
            return;
        }
        emailService.sendHtml(admins, subject, body);
    }

    private List<String> adminEmails() {
        List<String> emails = new ArrayList<>();
        for (Role role : List.of(Role.ADMIN, Role.OFFICE_ADMIN)) {
            userRepository.findAllByRole(role).stream()
                    .filter(User::isEnabled)
                    .map(User::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .forEach(emails::add);
        }
        return emails.stream().distinct().toList();
    }

    /** Notifications must never break the business action that triggered them. */
    private void quietly(Runnable action, String what) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Could not send the {}: {}", what, e.getMessage());
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("<", "&lt;").replace(">", "&gt;");
    }
}

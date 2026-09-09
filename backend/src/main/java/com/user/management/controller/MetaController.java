package com.user.management.controller;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Gender;
import com.user.management.entity.RequestStatus;
import com.user.management.entity.Role;
import com.user.management.entity.SewaType;
import com.user.management.integration.EmailService;
import com.user.management.integration.NotificationService;
import com.user.management.integration.WhatsAppService;
import com.user.management.model.ContactMessageRequest;
import com.user.management.model.OptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "8. Reference data", description = "Dropdown values, the About text and the Contact form.")
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    @Operation(summary = "All dropdown values in one call")
    @GetMapping("/options")
    public Map<String, List<OptionResponse>> options() {
        return Map.of(
                "roles", Arrays.stream(Role.values())
                        .map(r -> new OptionResponse(r.name(), r.getDisplayName())).toList(),
                "sewaTypes", Arrays.stream(SewaType.values())
                        .map(s -> new OptionResponse(s.name(), s.getDisplayName())).toList(),
                "attendanceStatuses", Arrays.stream(AttendanceStatus.values())
                        .map(s -> new OptionResponse(s.name(), s.getDisplayName())).toList(),
                "requestStatuses", Arrays.stream(RequestStatus.values())
                        .map(s -> new OptionResponse(s.name(), s.getDisplayName())).toList(),
                "genders", Arrays.stream(Gender.values())
                        .map(g -> new OptionResponse(g.name(), capitalise(g.name()))).toList());
    }

    @Operation(summary = "Which sharing channels are switched on")
    @GetMapping("/channels")
    public Map<String, Boolean> channels() {
        return Map.of(
                "email", emailService.isEnabled(),
                "whatsapp", whatsAppService.isEnabled());
    }

    @Operation(summary = "Send a message from the Contact screen to the office admins")
    @PostMapping("/contact")
    public ResponseEntity<Map<String, String>> contact(@Valid @RequestBody ContactMessageRequest request) {
        notificationService.sendContactMessage(request);
        return ResponseEntity.ok(Map.of("message",
                "Thank you. Your message has been sent to the office team."));
    }

    private String capitalise(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase();
    }
}

package com.user.management.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Bootstrap bootstrap = new Bootstrap();
    private Notification notification = new Notification();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMinutes = 480;
        private String issuer = "sewa-ums";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter
    @Setter
    public static class Bootstrap {
        private boolean enabled = true;
        private String adminUsername = "admin";
        private String adminPassword = "Admin@123";
        private String adminEmail = "admin@sewa.local";
    }

    @Getter
    @Setter
    public static class Notification {
        private Email email = new Email();
        private WhatsApp whatsapp = new WhatsApp();
    }

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = false;
        private String from = "no-reply@sewa.local";
    }

    @Getter
    @Setter
    public static class WhatsApp {
        private boolean enabled = false;
        private String apiUrl = "https://graph.facebook.com/v20.0";
        private String phoneNumberId;
        private String accessToken;
        private String defaultCountryCode = "91";
    }
}

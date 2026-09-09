package com.user.management.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI umsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sewadar User Management & Attendance API")
                        .version("1.0.0")
                        .description("""
                                Role based API for sewadar master data, attendance (roster sewa and
                                construction sewa), monthly reports, zone change requests and report
                                sharing over email and WhatsApp.

                                Sign in through POST /api/auth/login, then click Authorize and paste
                                the returned token to call the secured endpoints.
                                """)
                        .contact(new Contact().name("Sewa IT Team").email("it@sewa.local"))
                        .license(new License().name("Internal use")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT returned by /api/auth/login")));
    }
}

package com.user.management.config;

import com.user.management.AppInfo;
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
                        .title(AppInfo.NAME + " API")
                        .version("1.0.0")
                        .description("""
                                Role based API for sewadar master data, attendance (roster sewa and
                                construction sewa), monthly reports, zone change requests and report
                                sharing over email and WhatsApp.

                                Sign in through POST /api/auth/login, then click Authorize and paste
                                the returned token to call the secured endpoints.

                                Two things worth knowing before calling anything:

                                * **Every read is scoped to the caller.** A Zone Incharge asking for
                                  sewadars gets their own zones, a Sewadar gets their own record, and
                                  an Admin gets everything. The same endpoint returns different rows
                                  for different tokens - that is the design, not a filter you forgot.
                                * **Dates are calendar dates in the server's timezone**, sent as
                                  `yyyy-MM-dd`. Do not derive one from a UTC instant: east of
                                  Greenwich the UTC date is still yesterday for part of every
                                  morning, which silently writes attendance to the wrong day.
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

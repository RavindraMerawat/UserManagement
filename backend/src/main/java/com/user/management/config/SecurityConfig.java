package com.user.management.config;

import com.user.management.security.JwtAuthenticationFilter;
import com.user.management.security.RestAuthEntryPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoints.Unauthorized unauthorizedHandler;
    private final RestAuthEntryPoints.Forbidden forbiddenHandler;
    private final AppProperties properties;

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/public/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(unauthorizedHandler)
                        .accessDeniedHandler(forbiddenHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Account administration: Admin and Office Admin. UserService
                        // then stops an Office Admin creating or editing an ADMIN
                        // account, so the wider door cannot be used to grant yourself
                        // Admin.
                        .requestMatchers("/api/users/**").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        // Zone master data: read for everyone signed in, writes for admins.
                        .requestMatchers(HttpMethod.POST, "/api/zones/**").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/zones/**").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/zones/**").hasRole("ADMIN")
                        /*
                         * Sewadar master data changes. OFFICE_USER is on the list
                         * because the Office Sewadar designation grants
                         * manageSewadars; SewadarService then checks that grant, so
                         * an Office User without it is still refused - by the rule
                         * that knows their designation rather than by this one,
                         * which does not.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/sewadars")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        .requestMatchers(HttpMethod.PUT, "/api/sewadars/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/sewadars/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        // Construction sewa: everyone signed in may read it, and the
                        // service narrows that to their zones. Writing is office work;
                        // the designation rule in Capabilities is the finer check
                        // behind this one.
                        .requestMatchers(HttpMethod.POST, "/api/construction-sewa/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/construction-sewa/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        // Weekly seating sewa is badge work; the designation rule in
                        // Capabilities is the finer check behind this one.
                        .requestMatchers(HttpMethod.POST, "/api/weekly-seating/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        .requestMatchers(HttpMethod.DELETE, "/api/weekly-seating/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        /*
                         * Marking and editing attendance.
                         *
                         * OFFICE_USER was missing here while Capabilities granted
                         * every Office User markAttendance. The login told the
                         * screen it could mark, the screen offered Check in, and
                         * this line turned it into a 403 - the office could see the
                         * button and never use it. The designation matrix is the
                         * rule; this list only keeps a role that could never hold
                         * the grant from reaching the endpoint at all.
                         */
                        .requestMatchers(HttpMethod.POST, "/api/attendance/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER",
                                "COORDINATOR", "ZONE_INCHARGE", "SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/api/attendance/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER",
                                "COORDINATOR", "ZONE_INCHARGE", "SUPERVISOR")
                        // Deleting an entry is the manageSewadars grant, which
                        // AttendanceService.delete checks for the same reason.
                        .requestMatchers(HttpMethod.DELETE, "/api/attendance/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "OFFICE_USER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}

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
                        // Account administration is ADMIN only.
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        // Zone master data: read for everyone signed in, writes for admins.
                        .requestMatchers(HttpMethod.POST, "/api/zones/**").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/zones/**").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/zones/**").hasRole("ADMIN")
                        // Sewadar master data changes.
                        .requestMatchers(HttpMethod.POST, "/api/sewadars").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/sewadars/**").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/sewadars/**").hasAnyRole("ADMIN", "OFFICE_ADMIN")
                        // Marking and editing attendance.
                        .requestMatchers(HttpMethod.POST, "/api/attendance/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "COORDINATOR", "ZONE_INCHARGE", "SUPERVISOR")
                        .requestMatchers(HttpMethod.PUT, "/api/attendance/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN", "COORDINATOR", "ZONE_INCHARGE", "SUPERVISOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/attendance/**")
                        .hasAnyRole("ADMIN", "OFFICE_ADMIN")
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

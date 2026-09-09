package com.user.management.bootstrap;

import com.user.management.config.AppProperties;
import com.user.management.entity.Role;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds a few zones and the first ADMIN account so the application is usable on a
 * fresh database. Everything here is idempotent, so restarts are safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataBootstrap implements ApplicationRunner {

    private final AppProperties properties;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getBootstrap().isEnabled()) {
            return;
        }
        seedZones();
        seedAdmin();
    }

    private void seedZones() {
        if (zoneRepository.count() > 0) {
            return;
        }
        List<Zone> zones = List.of(
                Zone.builder().code("Z-01").name("Zone 1 - North").centre("Main Centre").active(true).build(),
                Zone.builder().code("Z-02").name("Zone 2 - South").centre("Main Centre").active(true).build(),
                Zone.builder().code("Z-03").name("Zone 3 - East").centre("Main Centre").active(true).build(),
                Zone.builder().code("Z-04").name("Zone 4 - West").centre("Main Centre").active(true).build());
        zoneRepository.saveAll(zones);
        log.info("Seeded {} zones", zones.size());
    }

    private void seedAdmin() {
        String username = properties.getBootstrap().getAdminUsername().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        User admin = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(properties.getBootstrap().getAdminPassword()))
                .fullName("System Administrator")
                .email(properties.getBootstrap().getAdminEmail())
                .role(Role.ADMIN)
                .enabled(true)
                .mustChangePassword(true)
                .build();
        userRepository.save(admin);
        log.warn("Created the first admin account \"{}\". Sign in and change the password immediately.",
                username);
    }
}

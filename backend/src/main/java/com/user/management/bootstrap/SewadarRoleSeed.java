package com.user.management.bootstrap;

import com.user.management.entity.SewadarRole;
import com.user.management.repository.SewadarRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Puts the fixed list of roles in place - the values the screens call designations.
 *
 * <p>Runs on every start and adds only what is missing, matched case-insensitively
 * by name. It never renames, deactivates or deletes, so a designation the office has
 * edited from Setup stays edited and one they have retired stays retired - this seed
 * cannot undo their work.</p>
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class SewadarRoleSeed implements ApplicationRunner {

    /** In the order the office reads them: seniority first, then the posts. */
    private static final List<String> ROLES = List.of(
            "Co-ordinator",
            "Zone Incharge",
            "Ass. Zone Incharge",
            "Supervisor",
            "Group Incharge",
            "Block Incharge",
            "Guide Sewa Incharge",
            "Office Incharge",
            "Office Sewadar",
            "Sewadar",
            "Guide Sewadar",
            "Filling Incharge",
            "Gate Incharge",
            "Outer Incharge LS",
            "Outer Incharge GS",
            "Side Filling Incharge");

    private final SewadarRoleRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int added = 0;
        for (String name : ROLES) {
            if (repository.findByNameIgnoreCase(name).isEmpty()) {
                repository.save(SewadarRole.builder().name(name).active(true).build());
                added++;
            }
        }
        if (added > 0) {
            log.info("Seeded {} role(s); {} are now on file", added, repository.count());
        }
    }
}

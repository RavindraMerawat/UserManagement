package com.user.management.security;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final SewadarRepository sewadarRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + username));

        /*
         * The linked sewadar is read for every login now, not only for SEWADAR.
         * Permissions come from that record's designation, so the lookup has to
         * happen whatever the role - a Co-ordinator's rights live on their sewadar
         * record, not on their account.
         *
         * sewadarId still narrows the data scope for SEWADAR only; for everyone
         * else it stays null, because being linked to a record does not mean you
         * may see only that record.
         */
        var linked = sewadarRepository.findByUserId(user.getId());

        Long sewadarId = user.getRole() == Role.SEWADAR
                ? linked.map(Sewadar::getId).orElse(null)
                : null;

        /*
         * The account's own role first. It is set on the account form, so it is the
         * direct answer; the linked sewadar record is the fallback for logins made
         * before accounts carried one.
         */
        String designation = user.getSewadarRole() != null
                ? user.getSewadarRole().getName()
                : linked.map(Sewadar::getRole)
                        .map(com.user.management.entity.SewadarRole::getName)
                        .orElse(null);

        /*
          * A co-ordinator's reach is recorded on their sewadar record - their own
          * zone and the others they cover - so the login sees all of them. Without
          * this the extra zones would be written down and mean nothing.
          */
        Set<Long> covered = linked.map(sewadar -> {
            Set<Long> ids = new LinkedHashSet<>();
            if (sewadar.getZone() != null) {
                ids.add(sewadar.getZone().getId());
            }
            sewadar.getExtraZones().forEach(zone -> ids.add(zone.getId()));
            return ids;
        }).orElseGet(LinkedHashSet::new);

        return new AppUserPrincipal(user, sewadarId, designation, covered);
    }
}

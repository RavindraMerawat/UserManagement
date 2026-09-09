package com.user.management.security;

import com.user.management.entity.Role;
import com.user.management.entity.User;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Long sewadarId = null;
        if (user.getRole() == Role.SEWADAR) {
            sewadarId = sewadarRepository.findByUserId(user.getId())
                    .map(s -> s.getId())
                    .orElse(null);
        }
        return new AppUserPrincipal(user, sewadarId);
    }
}

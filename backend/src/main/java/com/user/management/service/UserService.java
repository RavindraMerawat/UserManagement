package com.user.management.service;

import com.user.management.entity.Role;
import com.user.management.entity.SewadarRole;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Photo;
import com.user.management.entity.PhotoOwnerType;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.CreateUserRequest;
import com.user.management.model.PageResponse;
import com.user.management.model.UpdateUserRequest;
import com.user.management.model.TabCountsResponse;
import com.user.management.model.UserResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Account administration. Restricted to ADMIN by {@code SecurityConfig}.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final SewadarRepository sewadarRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhotoService photoService;
    private final CurrentUserService currentUser;
    private final com.user.management.repository.SewadarRoleRepository sewadarRoleRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, Role role, Long roleId, Boolean enabled,
                                             Pageable pageable) {
        String q = StringUtils.hasText(query) ? query.toLowerCase() : null;
        Page<User> page = userRepository.search(q, role, roleId, enabled, pageable);

        // The sewadar behind each account, for the photo it points at.
        Map<Long, Sewadar> linked = linkedSewadars(page.getContent());
        return PageResponse.of(page, user -> UserResponse.from(user, linked.get(user.getId())));
    }

    /**
     * Account id to the sewadar record that belongs to it, for a page of accounts.
     *
     * <p>Two ways of finding the same person, in order. The link proper is
     * {@code sewadars.user_id}, set when an account is made from a GR. No. The
     * accounts that predate that link have no back-pointer, so an account still
     * holding the GR. No the office typed is matched on the number instead - which
     * is what the office means by the link in the first place.</p>
     *
     * <p>Two queries for a page rather than one per row, and the second only runs
     * if the first left somebody unmatched.</p>
     */
    private Map<Long, Sewadar> linkedSewadars(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, Sewadar> byUser = new HashMap<>();
        for (Sewadar sewadar : sewadarRepository.findByUserIdIn(ids)) {
            if (sewadar.getUser() != null) {
                byUser.put(sewadar.getUser().getId(), sewadar);
            }
        }

        List<String> unlinkedBadges = users.stream()
                .filter(user -> !byUser.containsKey(user.getId()))
                .map(User::getBadgeNo)
                .filter(StringUtils::hasText)
                .toList();
        if (unlinkedBadges.isEmpty()) {
            return byUser;
        }

        Map<String, Sewadar> byBadge = new HashMap<>();
        for (Sewadar sewadar : sewadarRepository.findByBadgeNumberIgnoreCaseIn(unlinkedBadges)) {
            byBadge.put(sewadar.getBadgeNumber().toLowerCase(), sewadar);
        }
        for (User user : users) {
            if (byUser.containsKey(user.getId()) || !StringUtils.hasText(user.getBadgeNo())) {
                continue;
            }
            Sewadar found = byBadge.get(user.getBadgeNo().trim().toLowerCase());
            if (found != null) {
                byUser.put(user.getId(), found);
            }
        }
        return byUser;
    }

    /** The numbers on the tab strip. */
    @Transactional(readOnly = true)
    public TabCountsResponse tabCounts() {
        return TabCountsResponse.of(
                userRepository.countByStatus(null),
                "active", userRepository.countByStatus(true),
                "inactive", userRepository.countByStatus(false));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        User user = getEntity(id);
        // The edit dialog shows the sewadar's photo where the account has none of
        // its own, so one account needs the same link a page of them gets.
        return UserResponse.from(user, linkedSewadars(List.of(user)).get(user.getId()));
    }

    @Transactional(readOnly = true)
    public User getEntity(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new BadRequestException("Username " + request.username() + " is already taken");
        }
        // The form sends a role from the roles table; the coarse account type is
        // worked out from it unless one was sent explicitly.
        SewadarRole chosen = roleOf(request.roleId());
        Role accountType = request.role() != null ? request.role() : accountTypeFor(chosen);

        validateRoleWiring(accountType, request.zoneIds(), request.sewadarId());
        requireCanAssignRole(accountType);
        requireEmailIsFree(request.email(), null);

        User user = User.builder()
                .username(request.username().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                /*
                 * An empty box is "no email", not an email that happens to be empty.
                 * emailId is unique, and MySQL allows any number of NULLs but only
                 * one empty string - so the first account saved without an address
                 * took '' and every one after it collided with it. The report was
                 * "duplicate badge number" on a GR. No that was not duplicated at
                 * all. Update already normalised this; create did not.
                 */
                .email(trimToNull(request.email()))
                .mobile(trimToNull(request.mobile()))
                .role(accountType)
                .sewadarRole(chosen)
                .badgeNo(trimToNull(request.badgeNumber()))
                .gender(request.gender())
                .zones(resolveZones(accountType, request.zoneIds()))
                .enabled(true)
                .mustChangePassword(Boolean.TRUE.equals(request.mustChangePassword()))
                .build();
        User saved = userRepository.save(user);

        /*
         * The link is made for whatever the account type is, not for SEWADAR alone.
         * A co-ordinator's zones are recorded on their sewadar record - their own and
         * the others they cover - and that reach only reaches the login through this
         * link. Narrowing a login to a single record is still a SEWADAR-only rule;
         * that is decided in AppUserDetailsService, not here.
         */
        if (request.sewadarId() != null) {
            Sewadar sewadar = sewadarRepository.findById(request.sewadarId())
                    .orElseThrow(() -> NotFoundException.of("Sewadar", request.sewadarId()));
            if (sewadar.getUser() != null) {
                throw new BadRequestException("This sewadar already has a login: "
                        + sewadar.getUser().getUsername());
            }
            sewadar.setUser(saved);
            sewadarRepository.save(sewadar);
        }
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = getEntity(id);
        requireCanAdministrate(user);
        SewadarRole chosen = roleOf(request.roleId());
        Role accountType = request.role() != null
                ? request.role()
                : (chosen != null ? accountTypeFor(chosen) : null);

        if (accountType != null) {
            requireCanAssignRole(accountType);
        }
        if (chosen != null) {
            user.setSewadarRole(chosen);
        }

        if (StringUtils.hasText(request.username())) {
            String wanted = request.username().trim().toLowerCase();
            if (!wanted.equals(user.getUsername())) {
                userRepository.findByUsernameIgnoreCase(wanted).ifPresent(other -> {
                    throw new BadRequestException("That username is already taken. Choose another.");
                });
                log.info("{} renamed account {} to {}", currentUser.username(), user.getUsername(), wanted);
                user.setUsername(wanted);
            }
        }
        if (StringUtils.hasText(request.fullName())) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null) {
            requireEmailIsFree(request.email(), user);
            user.setEmail(trimToNull(request.email()));
        }
        if (request.mobile() != null) {
            user.setMobile(request.mobile());
        }
        if (request.badgeNumber() != null) {
            user.setBadgeNo(trimToNull(request.badgeNumber()));
        }
        /*
         * The gender decides which register this account reads, so clearing it is a
         * real choice - "this account sees both again" - and is kept distinct from
         * leaving the field out of the request, which changes nothing.
         */
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (accountType != null && accountType != user.getRole()) {
            if (accountType == Role.SEWADAR) {
                throw new BadRequestException(
                        "Switch an account to the Sewadar role by creating a login from the sewadar record");
            }
            if (user.getRole() == Role.ADMIN && countOtherEnabledAdmins(user) == 0) {
                throw new BadRequestException("At least one enabled Admin account must remain");
            }
            user.setRole(accountType);
            user.setZones(resolveZones(accountType, request.zoneIds()));
        } else if (request.zoneIds() != null) {
            user.setZones(resolveZones(user.getRole(), request.zoneIds()));
        }
        if (request.enabled() != null) {
            if (!request.enabled() && user.getRole() == Role.ADMIN && countOtherEnabledAdmins(user) == 0) {
                throw new BadRequestException("At least one enabled Admin account must remain");
            }
            user.setEnabled(request.enabled());
        }
        if (StringUtils.hasText(request.newPassword())) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            user.setMustChangePassword(true);
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        User user = getEntity(id);
        requireCanAdministrate(user);
        if (user.getRole() == Role.ADMIN && countOtherEnabledAdmins(user) == 0) {
            throw new BadRequestException("At least one enabled Admin account must remain");
        }
        // Detach the sewadar link first so the sewadar record survives.
        sewadarRepository.findByUserId(id).ifPresent(s -> {
            s.setUser(null);
            sewadarRepository.save(s);
        });
        // The photo lives in its own table, keyed by owner, with no foreign key to
        // cascade - so deleting the account here would otherwise leave the image
        // behind for ever, and the next account to be given this id would inherit
        // someone else's face.
        photoService.delete(PhotoOwnerType.USER, user.getId());
        userRepository.delete(user);
    }


    // ------------------------------------------------------------------- photo

    /*
     * Account administration is open to Admin and Office Admin, but only Admin may go
     * near an ADMIN account. Without this, an Office Admin could create an Admin login
     * - or promote their own - and quietly hold every permission in the application.
     * The URL rule in SecurityConfig opens the door; these two decide what is behind
     * it.
     */

    /**
     * An email address, where one is given, belongs to a single account - otherwise
     * "who is nikhil@..." has more than one answer, and a password reset sent by
     * address would be ambiguous.
     */
    private void requireEmailIsFree(String email, User current) {
        String cleaned = trimToNull(email);
        if (cleaned == null) {
            return;
        }
        if (current != null && cleaned.equalsIgnoreCase(current.getEmail())) {
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(cleaned)) {
            throw new BadRequestException(cleaned + " is already used by another account");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The account type implied by the chosen designation. */
    private Role accountTypeFor(SewadarRole role) {
        return role == null ? Role.SEWADAR : Role.forDesignation(role.getName());
    }

    /** Null clears it; an unknown id is a bad request rather than a silent null. */
    private SewadarRole roleOf(Long id) {
        if (id == null) {
            return null;
        }
        return sewadarRoleRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Role", id));
    }

    /** Refuses an Office Admin acting on an existing ADMIN account. */
    private void requireCanAdministrate(User target) {
        if (target.getRole() == Role.ADMIN && currentUser.role() != Role.ADMIN) {
            throw new ForbiddenException("Only an Admin can manage an Admin account");
        }
    }

    /** Refuses an Office Admin creating an ADMIN account or promoting one to it. */
    private void requireCanAssignRole(Role role) {
        if (role == Role.ADMIN && currentUser.role() != Role.ADMIN) {
            throw new ForbiddenException("Only an Admin can give an account the Admin role");
        }
    }

    @Transactional
    public UserResponse uploadPhoto(Long id, MultipartFile file) {
        User user = getEntity(id);
        requireCanAdministrate(user);
        Instant stamp = photoService.store(PhotoOwnerType.USER, user.getId(), file);
        user.setPhotoUpdatedAt(stamp);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Photo photo(Long id) {
        return photoService.get(PhotoOwnerType.USER, getEntity(id).getId());
    }

    @Transactional
    public UserResponse deletePhoto(Long id) {
        User user = getEntity(id);
        requireCanAdministrate(user);
        photoService.delete(PhotoOwnerType.USER, user.getId());
        user.setPhotoUpdatedAt(null);
        return UserResponse.from(userRepository.save(user));
    }

    private long countOtherEnabledAdmins(User user) {
        return userRepository.findAllByRole(Role.ADMIN).stream()
                .filter(User::isEnabled)
                .filter(u -> !u.getId().equals(user.getId()))
                .count();
    }

    private void validateRoleWiring(Role role, Set<Long> zoneIds, Long sewadarId) {
        if (role.isZoneScope() && (zoneIds == null || zoneIds.isEmpty())) {
            throw new BadRequestException("Assign at least one zone to a "
                    + role.getDisplayName() + " account");
        }
        if (role == Role.SEWADAR && sewadarId == null) {
            throw new BadRequestException("Link a sewadar record to a Sewadar account");
        }
    }

    private Set<Long> emptyIfNull(Set<Long> ids) {
        return ids == null ? Set.of() : ids;
    }

    private Set<Zone> resolveZones(Role role, Set<Long> zoneIds) {
        if (!role.isZoneScope()) {
            // Global-scope and sewadar accounts derive their scope elsewhere.
            return new LinkedHashSet<>();
        }
        Set<Zone> zones = new LinkedHashSet<>();
        for (Long zoneId : emptyIfNull(zoneIds)) {
            zones.add(zoneRepository.findById(zoneId)
                    .orElseThrow(() -> NotFoundException.of("Zone", zoneId)));
        }
        if (zones.isEmpty()) {
            throw new BadRequestException("Assign at least one zone to a " + role.getDisplayName() + " account");
        }
        return zones;
    }
}

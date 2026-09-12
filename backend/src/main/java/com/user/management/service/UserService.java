package com.user.management.service;

import com.user.management.entity.Role;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Account administration. Restricted to ADMIN by {@code SecurityConfig}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final SewadarRepository sewadarRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhotoService photoService;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, Role role, Boolean enabled, Pageable pageable) {
        String q = StringUtils.hasText(query) ? query.toLowerCase() : null;
        return PageResponse.of(userRepository.search(q, role, enabled, pageable), UserResponse::from);
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
        return UserResponse.from(getEntity(id));
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
        validateRoleWiring(request.role(), request.zoneIds(), request.sewadarId());
        requireCanAssignRole(request.role());
        requireEmailIsFree(request.email(), null);

        User user = User.builder()
                .username(request.username().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .email(request.email())
                .mobile(request.mobile())
                .role(request.role())
                .zones(resolveZones(request.role(), request.zoneIds()))
                .enabled(true)
                .mustChangePassword(Boolean.TRUE.equals(request.mustChangePassword()))
                .build();
        User saved = userRepository.save(user);

        if (request.role() == Role.SEWADAR) {
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
        if (request.role() != null) {
            requireCanAssignRole(request.role());
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
        if (request.role() != null && request.role() != user.getRole()) {
            if (request.role() == Role.SEWADAR) {
                throw new BadRequestException(
                        "Switch an account to the Sewadar role by creating a login from the sewadar record");
            }
            if (user.getRole() == Role.ADMIN && countOtherEnabledAdmins(user) == 0) {
                throw new BadRequestException("At least one enabled Admin account must remain");
            }
            user.setRole(request.role());
            user.setZones(resolveZones(request.role(), request.zoneIds()));
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

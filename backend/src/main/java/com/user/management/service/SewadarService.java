package com.user.management.service;

import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.PageResponse;
import com.user.management.model.SewadarRequest;
import com.user.management.model.SewadarResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.UserRepository;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Sewadar master data.
 *
 * <p>Reads are always narrowed by {@link DataScope}: a SEWADAR sees only their own
 * record, a ZONE_INCHARGE or SUPERVISOR only their zones, and ADMIN / OFFICE_ADMIN /
 * OFFICE_USER see everything. Writes are limited to ADMIN and OFFICE_ADMIN.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SewadarService {

    private static final String DEFAULT_SEWADAR_PASSWORD = "Sewa@12345";

    private final SewadarRepository sewadarRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final ZoneService zoneService;
    private final CurrentUserService currentUser;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<SewadarResponse> search(String query, Long zoneId, Boolean active, Pageable pageable) {
        DataScope scope = currentUser.scope();
        if (zoneId != null) {
            currentUser.requireZoneAccess(zoneId);
        }
        String q = StringUtils.hasText(query) ? query.toLowerCase() : null;
        return PageResponse.of(
                sewadarRepository.search(q, zoneId, active, scope.zoneIds(), scope.sewadarId(), pageable),
                SewadarResponse::from);
    }

    /** Active sewadars for the attendance sheet, narrowed to the caller's scope. */
    @Transactional(readOnly = true)
    public List<SewadarResponse> forAttendanceSheet(Long zoneId) {
        DataScope scope = currentUser.scope();
        if (zoneId != null) {
            currentUser.requireZoneAccess(zoneId);
        }
        List<Sewadar> sewadars = sewadarRepository.findForAttendanceSheet(zoneId, scope.zoneIds());
        return sewadars.stream()
                .filter(s -> scope.allowsSewadar(s.getId()))
                .map(SewadarResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SewadarResponse get(Long id) {
        return SewadarResponse.from(getEntityInScope(id));
    }

    /** The signed-in sewadar's own profile. */
    @Transactional(readOnly = true)
    public SewadarResponse me() {
        Long sewadarId = currentUser.principal().getSewadarId();
        if (sewadarId == null) {
            throw new BadRequestException("This account is not linked to a sewadar record");
        }
        return SewadarResponse.from(getEntityInScope(sewadarId));
    }

    /** Loads a sewadar and fails if it falls outside the caller's data scope. */
    @Transactional(readOnly = true)
    public Sewadar getEntityInScope(Long id) {
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));
        currentUser.requireSewadarAccess(sewadar.getId(), sewadar.getZone().getId());
        return sewadar;
    }

    @Transactional
    public SewadarResponse create(SewadarRequest request) {
        requireManagePermission();
        if (sewadarRepository.existsByBadgeNumberIgnoreCase(request.badgeNumber())) {
            throw new BadRequestException("Badge number " + request.badgeNumber() + " is already in use");
        }
        String aadhar = normaliseAadhar(request.aadharNumber());
        if (aadhar != null && sewadarRepository.existsByAadharNumber(aadhar)) {
            throw new BadRequestException("This Aadhaar number is already registered to another sewadar");
        }
        Zone zone = zoneService.getEntity(request.zoneId());

        Sewadar sewadar = Sewadar.builder()
                .badgeNumber(request.badgeNumber().trim())
                .name(request.name().trim())
                .fatherOrHusbandName(trimToNull(request.fatherOrHusbandName()))
                .dateOfBirth(request.dateOfBirth())
                .mobile(trimToNull(request.mobile()))
                .zone(zone)
                .address(trimToNull(request.address()))
                .aadharNumber(aadhar)
                .bloodGroup(trimToNull(request.bloodGroup()))
                .area(trimToNull(request.area()))
                .centerPoint(trimToNull(request.centerPoint()))
                .gender(request.gender())
                .email(trimToNull(request.email()))
                .city(trimToNull(request.city()))
                .pincode(trimToNull(request.pincode()))
                .department(trimToNull(request.department()))
                .primarySewaType(request.primarySewaType())
                .joiningDate(request.joiningDate())
                .active(request.active() == null || request.active())
                .build();

        Sewadar saved = sewadarRepository.save(sewadar);

        if (Boolean.TRUE.equals(request.createLogin())) {
            saved.setUser(createLoginFor(saved, request.loginUsername()));
            saved = sewadarRepository.save(saved);
        }
        return SewadarResponse.from(saved);
    }

    @Transactional
    public SewadarResponse update(Long id, SewadarRequest request) {
        requireManagePermission();
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));

        if (!sewadar.getBadgeNumber().equalsIgnoreCase(request.badgeNumber())
                && sewadarRepository.existsByBadgeNumberIgnoreCase(request.badgeNumber())) {
            throw new BadRequestException("Badge number " + request.badgeNumber() + " is already in use");
        }

        String aadhar = normaliseAadhar(request.aadharNumber());
        if (aadhar != null && !aadhar.equals(sewadar.getAadharNumber())
                && sewadarRepository.existsByAadharNumber(aadhar)) {
            throw new BadRequestException("This Aadhaar number is already registered to another sewadar");
        }

        sewadar.setBadgeNumber(request.badgeNumber().trim());

        // Registration form fields.
        sewadar.setName(request.name().trim());
        sewadar.setFatherOrHusbandName(trimToNull(request.fatherOrHusbandName()));
        sewadar.setDateOfBirth(request.dateOfBirth());
        sewadar.setMobile(trimToNull(request.mobile()));
        sewadar.setAddress(trimToNull(request.address()));
        sewadar.setAadharNumber(aadhar);
        sewadar.setBloodGroup(trimToNull(request.bloodGroup()));
        sewadar.setArea(trimToNull(request.area()));
        sewadar.setCenterPoint(trimToNull(request.centerPoint()));

        // Additional details.
        sewadar.setGender(request.gender());
        sewadar.setEmail(trimToNull(request.email()));
        sewadar.setCity(trimToNull(request.city()));
        sewadar.setPincode(trimToNull(request.pincode()));
        sewadar.setDepartment(trimToNull(request.department()));
        sewadar.setPrimarySewaType(request.primarySewaType());
        sewadar.setJoiningDate(request.joiningDate());
        if (request.active() != null) {
            sewadar.setActive(request.active());
        }
        if (request.zoneId() != null && !request.zoneId().equals(sewadar.getZone().getId())) {
            // A direct zone edit is an admin action; everyone else must raise a zone change request.
            sewadar.setZone(zoneService.getEntity(request.zoneId()));
        }
        if (Boolean.TRUE.equals(request.createLogin()) && sewadar.getUser() == null) {
            sewadar.setUser(createLoginFor(sewadar, request.loginUsername()));
        }
        return SewadarResponse.from(sewadarRepository.save(sewadar));
    }

    /**
     * Soft deletes a sewadar that already has attendance history, hard deletes one
     * that does not.
     */
    @Transactional
    public void delete(Long id) {
        requireManagePermission();
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));

        boolean hasHistory = !attendanceRepository
                .findBySewadarIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        id, java.time.LocalDate.of(1970, 1, 1), java.time.LocalDate.now().plusYears(50))
                .isEmpty();

        if (hasHistory) {
            sewadar.setActive(false);
            sewadarRepository.save(sewadar);
            log.info("Sewadar {} deactivated (attendance history retained)", sewadar.getBadgeNumber());
            return;
        }
        User login = sewadar.getUser();
        sewadar.setUser(null);
        sewadarRepository.save(sewadar);
        sewadarRepository.delete(sewadar);
        if (login != null) {
            userRepository.delete(login);
        }
    }

    private User createLoginFor(Sewadar sewadar, String requestedUsername) {
        String username = StringUtils.hasText(requestedUsername)
                ? requestedUsername.trim().toLowerCase()
                : sewadar.getBadgeNumber().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Username " + username + " is already taken");
        }
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(DEFAULT_SEWADAR_PASSWORD))
                .fullName(sewadar.getName())
                .email(sewadar.getEmail())
                .mobile(sewadar.getMobile())
                .role(Role.SEWADAR)
                .enabled(true)
                .mustChangePassword(true)
                .build();
        log.info("Created sewadar login {} (must change password on first sign in)", username);
        return userRepository.save(user);
    }

    private void requireManagePermission() {
        if (!currentUser.canManageSewadars()) {
            throw new ForbiddenException("Your role cannot add, edit or delete sewadar records");
        }
    }

    /**
     * Strips the spaces and dashes people type into an Aadhaar field so the stored
     * value is always 12 bare digits and the uniqueness check cannot be fooled by
     * formatting. Returns null for a blank value, which keeps the unique constraint
     * happy for sewadars whose number has not been collected yet.
     */
    private String normaliseAadhar(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() != 12) {
            throw new BadRequestException("Aadhaar number must be 12 digits");
        }
        return digits;
    }

    /** Keeps blank form inputs out of the database as empty strings. */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

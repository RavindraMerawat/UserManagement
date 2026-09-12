package com.user.management.service;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Role;
import com.user.management.entity.Sewadar;
import com.user.management.entity.User;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.PageResponse;
import com.user.management.model.BadgeSummaryResponse;
import com.user.management.model.SewadarRequest;
import com.user.management.model.TabCountsResponse;
import com.user.management.model.SewadarResponse;
import com.user.management.repository.AttendanceRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.entity.Photo;
import com.user.management.entity.PhotoOwnerType;
import com.user.management.repository.UserRepository;
import com.user.management.security.AadharMask;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
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
    private final PhotoService photoService;

    @Transactional(readOnly = true)
    public PageResponse<SewadarResponse> search(String query, Long zoneId, Boolean active, Pageable pageable) {
        DataScope scope = currentUser.scope();
        if (zoneId != null) {
            currentUser.requireZoneAccess(zoneId);
        }
        String q = StringUtils.hasText(query) ? query.toLowerCase() : null;
        return PageResponse.of(
                sewadarRepository.search(q, zoneId, active, scope.zoneIds(), scope.sewadarId(), pageable),
                this::toResponse);
    }

    /**
     * The people behind one dashboard tile.
     *
     * <p>A null {@code status} is the Total tile - every active sewadar in scope. A
     * status is one of the day tiles: the sewadars who have an attendance record with
     * that status on that date. It runs the same query {@code DashboardService} counts
     * with, so the grid always holds exactly as many rows as the tile said.</p>
     *
     * <p>Scope applies here as it does everywhere else: global roles see every zone,
     * a zone-scoped role only its own zones, and a Sewadar only themselves. Nothing
     * about being reached from a dashboard tile widens that.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<SewadarResponse> dashboardList(AttendanceStatus status,
                                                       LocalDate onDate,
                                                       Pageable pageable) {
        DataScope scope = currentUser.scope();
        LocalDate date = onDate == null ? LocalDate.now() : onDate;
        return PageResponse.of(
                sewadarRepository.findForDashboard(
                        status, date, scope.zoneIds(), scope.sewadarId(), pageable),
                this::toResponse);
    }

    /**
     * The numbers on the tab strip, each taken with the caller's scope applied, so a
     * zone role's "All" is their zones rather than the whole register.
     */
    @Transactional(readOnly = true)
    public TabCountsResponse tabCounts() {
        DataScope scope = currentUser.scope();
        return TabCountsResponse.of(
                sewadarRepository.countInScope(null, scope.zoneIds(), scope.sewadarId()),
                "active", sewadarRepository.countInScope(true, scope.zoneIds(), scope.sewadarId()),
                "inactive", sewadarRepository.countInScope(false, scope.zoneIds(), scope.sewadarId()));
    }

    /** Badge totals, restricted to the same scope as every sewadar search. */
    @Transactional(readOnly = true)
    public BadgeSummaryResponse badgeSummary() {
        DataScope scope = currentUser.scope();
        long issued = sewadarRepository.countBadges(scope.zoneIds(), scope.sewadarId(), true, null);
        long received = sewadarRepository.countBadges(scope.zoneIds(), scope.sewadarId(), true, true);
        long pending = sewadarRepository.countBadges(scope.zoneIds(), scope.sewadarId(), false, null);
        return new BadgeSummaryResponse(issued, received, pending);
    }

    @Transactional
    public SewadarResponse issueBadge(Long id) {
        requireBadgePermission();
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));
        if (sewadar.isBadgeIssued()) {
            throw new BadRequestException("Badge has already been issued to this sewadar");
        }
        sewadar.setBadgeIssued(true);
        return toResponse(sewadarRepository.save(sewadar));
    }

    @Transactional
    public SewadarResponse receiveBadge(Long id) {
        requireBadgePermission();
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));
        if (!sewadar.isBadgeIssued()) {
            throw new BadRequestException("Issue the badge before marking it received");
        }
        if (sewadar.isBadgeReceived()) {
            throw new BadRequestException("Badge has already been marked as received");
        }
        sewadar.setBadgeReceived(true);
        return toResponse(sewadarRepository.save(sewadar));
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
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SewadarResponse get(Long id) {
        return toResponse(getEntityInScope(id));
    }

    /** The signed-in sewadar's own profile. */
    @Transactional(readOnly = true)
    public SewadarResponse me() {
        Long sewadarId = currentUser.principal().getSewadarId();
        if (sewadarId == null) {
            throw new BadRequestException("This account is not linked to a sewadar record");
        }
        return toResponse(getEntityInScope(sewadarId));
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
        String badgeNumber = normaliseBadgeNumber(request.badgeNumber());
        if (sewadarRepository.existsByBadgeNumberIgnoreCase(badgeNumber)) {
            throw new BadRequestException("Badge number " + badgeNumber + " is already in use");
        }
        String aadhar = normaliseAadhar(request.aadharNumber());
        if (aadhar != null && sewadarRepository.existsByAadharNumber(aadhar)) {
            throw new BadRequestException("This Aadhaar number is already registered to another sewadar");
        }
        String email = trimToNull(request.email());
        if (email != null && sewadarRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException(email + " is already on another sewadar record");
        }
        Zone zone = zoneService.getEntity(request.zoneId());

        Sewadar sewadar = Sewadar.builder()
                .badgeNumber(badgeNumber)
                .badgeIssued(Boolean.TRUE.equals(request.badgeReceived()))
                .badgeReceived(Boolean.TRUE.equals(request.badgeReceived()))
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
                .email(email)
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
        return toResponse(saved);
    }

    @Transactional
    public SewadarResponse update(Long id, SewadarRequest request) {
        requireManagePermission();
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));
        String badgeNumber = normaliseBadgeNumber(request.badgeNumber());

        // MySQL's usual collation treats trailing spaces as equal. Normalising both
        // sides avoids an unchanged badge such as "B00123 " conflicting with itself.
        if (!normaliseBadgeNumber(sewadar.getBadgeNumber()).equalsIgnoreCase(badgeNumber)
                && sewadarRepository.existsByBadgeNumberIgnoreCase(badgeNumber)) {
            throw new BadRequestException("Badge number " + badgeNumber + " is already in use");
        }

        String aadhar = normaliseAadhar(request.aadharNumber());
        if (aadhar != null && !aadhar.equals(sewadar.getAadharNumber())
                && sewadarRepository.existsByAadharNumber(aadhar)) {
            throw new BadRequestException("This Aadhaar number is already registered to another sewadar");
        }
        String email = trimToNull(request.email());
        if (email != null && !email.equalsIgnoreCase(sewadar.getEmail())
                && sewadarRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException(email + " is already on another sewadar record");
        }

        sewadar.setBadgeNumber(badgeNumber);
        if (request.badgeReceived() != null) {
            if (request.badgeReceived()) {
                sewadar.setBadgeIssued(true);
            }
            sewadar.setBadgeReceived(request.badgeReceived());
        }

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
        sewadar.setEmail(email);
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
        return toResponse(sewadarRepository.save(sewadar));
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
        // Same as on the account side: photos are keyed by owner with nothing to
        // cascade, so they have to be removed deliberately or they outlive the row.
        photoService.delete(PhotoOwnerType.SEWADAR, sewadar.getId());
        sewadarRepository.delete(sewadar);
        if (login != null) {
            photoService.delete(PhotoOwnerType.USER, login.getId());
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


    // ------------------------------------------------------------------- photo

    /** Uploads or replaces the sewadar photo. Admin and Office Admin only. */
    @Transactional
    public SewadarResponse uploadPhoto(Long id, MultipartFile file) {
        requireManagePermission();
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));

        Instant stamp = photoService.store(PhotoOwnerType.SEWADAR, sewadar.getId(), file);
        sewadar.setPhotoUpdatedAt(stamp);
        return toResponse(sewadarRepository.save(sewadar));
    }

    /** Reads the photo, narrowed to the caller's data scope like any other field. */
    @Transactional(readOnly = true)
    public Photo photo(Long id) {
        Sewadar sewadar = getEntityInScope(id);
        return photoService.get(PhotoOwnerType.SEWADAR, sewadar.getId());
    }

    @Transactional
    public SewadarResponse deletePhoto(Long id) {
        requireManagePermission();
        Sewadar sewadar = sewadarRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewadar", id));

        photoService.delete(PhotoOwnerType.SEWADAR, sewadar.getId());
        sewadar.setPhotoUpdatedAt(null);
        return toResponse(sewadarRepository.save(sewadar));
    }

    /**
     * Maps one sewadar, masking the Aadhaar number unless the caller's role may see
     * it in full. Every read path goes through here, so the decision is made once.
     */
    private SewadarResponse toResponse(Sewadar sewadar) {
        return SewadarResponse.from(sewadar, currentUser.canViewFullAadhar(sewadar.getId()));
    }

    /** Issuing and collecting a badge is an office action - see canManageBadges. */
    private void requireBadgePermission() {
        if (!currentUser.canManageBadges()) {
            throw new ForbiddenException(
                    "Your role can view badge details but cannot issue or collect a badge");
        }
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
        if (AadharMask.looksMasked(value)) {
            // A masked value came back from a screen that was never shown the real
            // number. Saving it would silently overwrite a good number with four digits.
            throw new BadRequestException(
                    "That Aadhaar number is masked. Enter all 12 digits, or leave the field as it was.");
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

    private String normaliseBadgeNumber(String value) {
        return value == null ? "" : value.trim();
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

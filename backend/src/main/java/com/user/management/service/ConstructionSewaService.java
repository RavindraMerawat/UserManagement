package com.user.management.service;

import com.user.management.entity.ConstructionSewa;
import com.user.management.entity.Sewadar;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.ConstructionSewaRequest;
import com.user.management.model.ConstructionSewaResponse;
import com.user.management.model.PageResponse;
import com.user.management.repository.ConstructionSewaRepository;
import com.user.management.repository.SewadarRepository;
import com.user.management.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * The construction sewa register: one entry per sewadar per day.
 *
 * <p>Always read one sewadar at a time. The screen finds somebody by GR. No, name or
 * mobile and shows their days; there is no list of everybody, because the question
 * this register answers is always about a particular person.</p>
 *
 * <p>Reading is open to everyone who can see the sewadar screens, within their own
 * zones. Recording is office work - Admin, Office Incharge and Office Sewadar - and
 * the zone trio may look but not write, which is the rule
 * {@link com.user.management.security.Capabilities} states.</p>
 */
@Service
@RequiredArgsConstructor
public class ConstructionSewaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private final ConstructionSewaRepository repository;
    private final SewadarRepository sewadarRepository;
    private final CurrentUserService currentUser;

    /** Everything recorded for one sewadar, newest day first. */
    @Transactional(readOnly = true)
    public PageResponse<ConstructionSewaResponse> forSewadar(Long sewadarId, Pageable pageable) {
        Sewadar sewadar = readableSewadar(sewadarId);
        return PageResponse.of(
                repository.findBySewadarIdOrderBySewaDateDesc(sewadar.getId(), pageable),
                ConstructionSewaResponse::from);
    }

    /** How many days this sewadar has done. */
    @Transactional(readOnly = true)
    public long countFor(Long sewadarId) {
        return repository.countBySewadarId(readableSewadar(sewadarId).getId());
    }

    /**
     * Records one day.
     *
     * <p>The same day twice is refused rather than merged or counted again: the
     * office records these from paper, often days later, and the commonest mistake
     * is entering a day that is already in. Saying so by name and date is more use
     * than a second row nobody notices.</p>
     */
    @Transactional
    public ConstructionSewaResponse record(ConstructionSewaRequest request) {
        requireManagePermission();
        Sewadar sewadar = readableSewadar(request.sewadarId());

        if (repository.existsBySewadarIdAndSewaDate(sewadar.getId(), request.sewaDate())) {
            throw new BadRequestException("Construction sewa for " + sewadar.getName()
                    + " on " + request.sewaDate().format(DAY)
                    + " is already recorded. One day counts once.");
        }

        ConstructionSewa saved = repository.save(ConstructionSewa.builder()
                .sewadar(sewadar)
                .sewaDate(request.sewaDate())
                .remarks(trimToNull(request.remarks()))
                .build());
        return ConstructionSewaResponse.from(saved);
    }

    /** Removes one recorded day - how a wrong date is put right. */
    @Transactional
    public void delete(Long id) {
        requireManagePermission();
        ConstructionSewa row = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Construction sewa", id));
        readableSewadar(row.getSewadar().getId());
        repository.delete(row);
    }

    /** The sewadar, if this person is allowed to see them at all. */
    private Sewadar readableSewadar(Long sewadarId) {
        Sewadar sewadar = sewadarRepository.findById(sewadarId)
                .orElseThrow(() -> NotFoundException.of("Sewadar", sewadarId));
        currentUser.requireSewadarAccess(sewadar.getId(),
                sewadar.getZone() == null ? null : sewadar.getZone().getId());
        return sewadar;
    }

    private void requireManagePermission() {
        if (!currentUser.canManageConstruction()) {
            throw new ForbiddenException(
                    "Your role can view construction sewa but cannot record or change it");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

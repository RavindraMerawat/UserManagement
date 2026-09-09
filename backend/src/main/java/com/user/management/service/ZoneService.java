package com.user.management.service;

import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.ZoneRequest;
import com.user.management.model.ZoneResponse;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.ZoneRepository;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final SewadarRepository sewadarRepository;
    private final CurrentUserService currentUser;

    /**
     * Zones the signed-in user may pick from. Zone-scoped roles only see their own
     * zones; a sewadar sees every active zone so they can raise a change request.
     */
    @Transactional(readOnly = true)
    public List<ZoneResponse> listAccessible(boolean includeInactive) {
        DataScope scope = currentUser.scope();
        List<Zone> zones = includeInactive
                ? zoneRepository.findAllByOrderByNameAsc()
                : zoneRepository.findAllByActiveTrueOrderByNameAsc();

        if (scope.zoneIds() == null) {
            return zones.stream().map(ZoneResponse::from).toList();
        }
        // A sewadar may target any active zone when requesting a transfer.
        if (scope.sewadarId() != null) {
            return zones.stream().map(ZoneResponse::from).toList();
        }
        return zones.stream()
                .filter(z -> scope.zoneIds().contains(z.getId()))
                .map(ZoneResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> listAll() {
        return zoneRepository.findAllByOrderByNameAsc().stream().map(ZoneResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Zone getEntity(Long id) {
        return zoneRepository.findById(id).orElseThrow(() -> NotFoundException.of("Zone", id));
    }

    @Transactional(readOnly = true)
    public ZoneResponse get(Long id) {
        return ZoneResponse.from(getEntity(id));
    }

    @Transactional
    public ZoneResponse create(ZoneRequest request) {
        if (zoneRepository.existsByCodeIgnoreCase(request.code())) {
            throw new BadRequestException("A zone with code " + request.code() + " already exists");
        }
        Zone zone = Zone.builder()
                .code(request.code().trim())
                .name(request.name().trim())
                .description(request.description())
                .centre(request.centre())
                .active(request.active() == null || request.active())
                .build();
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse update(Long id, ZoneRequest request) {
        Zone zone = getEntity(id);
        if (!zone.getCode().equalsIgnoreCase(request.code())
                && zoneRepository.existsByCodeIgnoreCase(request.code())) {
            throw new BadRequestException("A zone with code " + request.code() + " already exists");
        }
        zone.setCode(request.code().trim());
        zone.setName(request.name().trim());
        zone.setDescription(request.description());
        zone.setCentre(request.centre());
        if (request.active() != null) {
            zone.setActive(request.active());
        }
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    /**
     * Deactivates the zone. A zone that still has sewadars is never hard deleted so
     * historic attendance keeps pointing at a valid zone.
     */
    @Transactional
    public void delete(Long id) {
        Zone zone = getEntity(id);
        long sewadars = sewadarRepository.countByZoneIdInAndActiveTrue(List.of(id));
        if (sewadars > 0) {
            zone.setActive(false);
            zoneRepository.save(zone);
            return;
        }
        zoneRepository.delete(zone);
    }
}

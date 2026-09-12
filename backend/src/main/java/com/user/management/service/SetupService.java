package com.user.management.service;

import com.user.management.entity.Area;
import com.user.management.entity.SatsangPoint;
import com.user.management.entity.Zone;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.model.AreaRequest;
import com.user.management.model.AreaResponse;
import com.user.management.model.SatsangPointRequest;
import com.user.management.model.SatsangPointResponse;
import com.user.management.repository.AreaRepository;
import com.user.management.repository.SatsangPointRepository;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The Setup screen's master data: areas and satsang points.
 *
 * <p>Zones are managed by {@link ZoneService} and shown on the same screen; these are
 * the two levels beneath it. Reads follow the caller's zone scope like everything
 * else, so a Zone Incharge opening Setup sees only their own zones' areas. Writes are
 * Admin and Office Admin - the same pair that owns the zone list.</p>
 *
 * <p>Nothing in use is ever hard deleted: an area that still has points under it is
 * deactivated instead, so records already naming it keep resolving.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupService {

    private final AreaRepository areaRepository;
    private final SatsangPointRepository pointRepository;
    private final ZoneService zoneService;
    private final CurrentUserService currentUser;

    // ------------------------------------------------------------------ areas

    @Transactional(readOnly = true)
    public List<AreaResponse> listAreas(Long zoneId, Boolean active) {
        requireSetupAccess();
        DataScope scope = currentUser.scope();
        if (zoneId != null) {
            currentUser.requireZoneAccess(zoneId);
        }
        return areaRepository.findInScope(zoneId, active, scope.zoneIds()).stream()
                .map(AreaResponse::from)
                .toList();
    }

    @Transactional
    public AreaResponse createArea(AreaRequest request) {
        requireSetupPermission();
        Zone zone = zoneService.getEntity(request.zoneId());
        String name = clean(request.name());
        if (areaRepository.existsByZoneIdAndNameIgnoreCase(zone.getId(), name)) {
            throw new BadRequestException(
                    "There is already an area called " + name + " in " + zone.getName());
        }
        Area area = areaRepository.save(Area.builder()
                .name(name)
                .zone(zone)
                .active(request.active() == null || request.active())
                .build());
        log.info("Area {} added to zone {}", name, zone.getName());
        return AreaResponse.from(area);
    }

    @Transactional
    public AreaResponse updateArea(Long id, AreaRequest request) {
        requireSetupPermission();
        Area area = areaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Area", id));
        Zone zone = zoneService.getEntity(request.zoneId());
        String name = clean(request.name());

        boolean moved = !zone.getId().equals(area.getZone().getId());
        boolean renamed = !name.equalsIgnoreCase(area.getName());
        if ((moved || renamed) && areaRepository.existsByZoneIdAndNameIgnoreCase(zone.getId(), name)) {
            throw new BadRequestException(
                    "There is already an area called " + name + " in " + zone.getName());
        }

        area.setName(name);
        area.setZone(zone);
        if (request.active() != null) {
            area.setActive(request.active());
        }
        return AreaResponse.from(areaRepository.save(area));
    }

    @Transactional
    public void deleteArea(Long id) {
        requireSetupPermission();
        Area area = areaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Area", id));

        // An area with points under it is still referenced, so it is retired rather
        // than removed - the same rule zones and sewadars follow.
        if (pointRepository.countByAreaIdAndActiveTrue(area.getId()) > 0) {
            area.setActive(false);
            areaRepository.save(area);
            log.info("Area {} deactivated (it still has satsang points)", area.getName());
            return;
        }
        areaRepository.delete(area);
    }

    // ---------------------------------------------------------- satsang points

    @Transactional(readOnly = true)
    public List<SatsangPointResponse> listPoints(Long areaId, Long zoneId, Boolean active) {
        requireSetupAccess();
        DataScope scope = currentUser.scope();
        if (zoneId != null) {
            currentUser.requireZoneAccess(zoneId);
        }
        return pointRepository.findInScope(areaId, zoneId, active, scope.zoneIds()).stream()
                .map(SatsangPointResponse::from)
                .toList();
    }

    @Transactional
    public SatsangPointResponse createPoint(SatsangPointRequest request) {
        requireSetupPermission();
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> NotFoundException.of("Area", request.areaId()));
        String name = clean(request.name());
        if (pointRepository.existsByAreaIdAndNameIgnoreCase(area.getId(), name)) {
            throw new BadRequestException("There is already a satsang point called " + name
                    + " in " + area.getName());
        }
        SatsangPoint point = pointRepository.save(SatsangPoint.builder()
                .name(name)
                .area(area)
                .active(request.active() == null || request.active())
                .build());
        log.info("Satsang point {} added to area {}", name, area.getName());
        return SatsangPointResponse.from(point);
    }

    @Transactional
    public SatsangPointResponse updatePoint(Long id, SatsangPointRequest request) {
        requireSetupPermission();
        SatsangPoint point = pointRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Satsang point", id));
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> NotFoundException.of("Area", request.areaId()));
        String name = clean(request.name());

        boolean moved = !area.getId().equals(point.getArea().getId());
        boolean renamed = !name.equalsIgnoreCase(point.getName());
        if ((moved || renamed) && pointRepository.existsByAreaIdAndNameIgnoreCase(area.getId(), name)) {
            throw new BadRequestException("There is already a satsang point called " + name
                    + " in " + area.getName());
        }

        point.setName(name);
        point.setArea(area);
        if (request.active() != null) {
            point.setActive(request.active());
        }
        return SatsangPointResponse.from(pointRepository.save(point));
    }

    @Transactional
    public void deletePoint(Long id) {
        requireSetupPermission();
        SatsangPoint point = pointRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Satsang point", id));
        pointRepository.delete(point);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Setup belongs to Admin and Office Admin for reading as well as writing. The
     * zone roles never see the screen, and the sewadar form these lists feed is the
     * office pair's too, so nothing else needs them.
     */
    private void requireSetupAccess() {
        if (!currentUser.canManageSewadars()) {
            throw new ForbiddenException("Your role cannot open Setup");
        }
    }

    /** Setup is owned by the same pair that owns the zone list. */
    private void requireSetupPermission() {
        if (!currentUser.canManageSewadars()) {
            throw new ForbiddenException("Your role cannot change the setup lists");
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

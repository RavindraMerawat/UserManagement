package com.user.management.service;

import com.user.management.entity.Area;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.SewaPointRepository;
import com.user.management.repository.SewadarRoleRepository;
import com.user.management.model.SewaPointResponse;
import com.user.management.model.SewaPointRequest;
import com.user.management.model.SewadarRoleResponse;
import com.user.management.model.SewadarRoleRequest;
import com.user.management.entity.SewaPoint;
import com.user.management.entity.SewadarRole;
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
 * else. Areas and satsang points are flat lists, so everyone sees the same ones; only
 * zones are scoped. Writes are
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
    private final SewadarRepository sewadarRepository;
    private final SewadarRoleRepository sewadarRoleRepository;
    private final SewaPointRepository sewaPointRepository;

    // ------------------------------------------------------------------ areas

    @Transactional(readOnly = true)
    public List<AreaResponse> listAreas(Boolean active) {
        requireSetupAccess();
        return areaRepository.findAllInOrder(active).stream()
                .map(AreaResponse::from)
                .toList();
    }

    @Transactional
    public AreaResponse createArea(AreaRequest request) {
        requireSetupPermission();
        String name = clean(request.name());
        if (areaRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already an area called " + name);
        }
        Area area = areaRepository.save(Area.builder()
                .name(name)
                .active(request.active() == null || request.active())
                .build());
        log.info("Area {} added", name);
        return AreaResponse.from(area);
    }

    @Transactional
    public AreaResponse updateArea(Long id, AreaRequest request) {
        requireSetupPermission();
        Area area = areaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Area", id));
        String name = clean(request.name());

        if (!name.equalsIgnoreCase(area.getName()) && areaRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already an area called " + name);
        }

        area.setName(name);
        if (request.active() != null) {
            area.setActive(request.active());
        }
        return AreaResponse.from(areaRepository.save(area));
    }

    @Transactional
    public void deleteArea(Long id) {
        requireSetupPermission();
        Area area = areaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Area", id));

        /*
         * Areas are written onto sewadar records by name, so removing one that is in
         * use would leave those records pointing at a name no list offers. Retiring
         * it keeps the existing records readable and takes it out of the picker.
         */
        if (sewadarRepository.existsByAreaIgnoreCase(area.getName())) {
            area.setActive(false);
            areaRepository.save(area);
            log.info("Area {} deactivated (sewadars still carry it)", area.getName());
            return;
        }
        areaRepository.delete(area);
    }

    // ---------------------------------------------------------- satsang points

    @Transactional(readOnly = true)
    public List<SatsangPointResponse> listPoints(Boolean active) {
        requireSetupAccess();
        return pointRepository.findAllInOrder(active).stream()
                .map(SatsangPointResponse::from)
                .toList();
    }

    @Transactional
    public SatsangPointResponse createPoint(SatsangPointRequest request) {
        requireSetupPermission();
        String name = clean(request.name());
        if (pointRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already a satsang point called " + name);
        }
        SatsangPoint point = pointRepository.save(SatsangPoint.builder()
                .name(name)
                .active(request.active() == null || request.active())
                .build());
        log.info("Satsang point {} added", name);
        return SatsangPointResponse.from(point);
    }

    @Transactional
    public SatsangPointResponse updatePoint(Long id, SatsangPointRequest request) {
        requireSetupPermission();
        SatsangPoint point = pointRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Satsang point", id));
        String name = clean(request.name());

        if (!name.equalsIgnoreCase(point.getName()) && pointRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already a satsang point called " + name);
        }

        point.setName(name);
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

        if (sewadarRepository.existsByCenterPointIgnoreCase(point.getName())) {
            point.setActive(false);
            pointRepository.save(point);
            log.info("Satsang point {} deactivated (sewadars still carry it)", point.getName());
            return;
        }
        pointRepository.delete(point);
    }


    // --------------------------------------------------------- designations

    /**
     * The designation list.
     *
     * <p>Readable by anyone who can open a sewadar form, because the form needs it
     * to render a dropdown. Changing it is Setup work.</p>
     */
    @Transactional(readOnly = true)
    public List<SewadarRoleResponse> listDesignations(Boolean active) {
        return (Boolean.TRUE.equals(active)
                ? sewadarRoleRepository.findByActiveTrueOrderByNameAsc()
                : sewadarRoleRepository.findAllByOrderByNameAsc())
                .stream().map(SewadarRoleResponse::from).toList();
    }

    @Transactional
    public SewadarRoleResponse createDesignation(SewadarRoleRequest request) {
        requireSetupPermission();
        String name = clean(request.text());
        if (sewadarRoleRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already a designation called " + name);
        }
        SewadarRole saved = sewadarRoleRepository.save(SewadarRole.builder()
                .name(name)
                .active(request.active() == null || request.active())
                .build());
        log.info("SewadarRole {} added", name);
        return SewadarRoleResponse.from(saved);
    }

    @Transactional
    public SewadarRoleResponse updateDesignation(Long id, SewadarRoleRequest request) {
        requireSetupPermission();
        SewadarRole role = sewadarRoleRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Designation", id));
        String name = clean(request.text());

        if (!name.equalsIgnoreCase(role.getName())
                && sewadarRoleRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already a designation called " + name);
        }
        role.setName(name);
        if (request.active() != null) {
            role.setActive(request.active());
        }
        return SewadarRoleResponse.from(sewadarRoleRepository.save(role));
    }

    /**
     * Retired rather than deleted once anyone holds it - the permission rules read
     * this name, and removing it under a live login would silently drop their
     * access.
     */
    @Transactional
    public void deleteDesignation(Long id) {
        requireSetupPermission();
        SewadarRole role = sewadarRoleRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Designation", id));
        if (sewadarRepository.countByRoleId(id) > 0) {
            role.setActive(false);
            sewadarRoleRepository.save(role);
            log.info("SewadarRole {} deactivated (sewadars still hold it)",
                    role.getName());
            return;
        }
        sewadarRoleRepository.delete(role);
    }

    // ---------------------------------------------------------- sewa points

    @Transactional(readOnly = true)
    public List<SewaPointResponse> listSewaPoints(Boolean active) {
        return (Boolean.TRUE.equals(active)
                ? sewaPointRepository.findByActiveTrueOrderByNameAsc()
                : sewaPointRepository.findAllByOrderByNameAsc())
                .stream().map(SewaPointResponse::from).toList();
    }

    @Transactional
    public SewaPointResponse createSewaPoint(SewaPointRequest request) {
        requireSetupPermission();
        String name = clean(request.name());
        if (sewaPointRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already a sewa point called " + name);
        }
        SewaPoint saved = sewaPointRepository.save(SewaPoint.builder()
                .name(name)
                .active(request.active() == null || request.active())
                .build());
        log.info("Sewa point {} added", name);
        return SewaPointResponse.from(saved);
    }

    @Transactional
    public SewaPointResponse updateSewaPoint(Long id, SewaPointRequest request) {
        requireSetupPermission();
        SewaPoint point = sewaPointRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewa point", id));
        String name = clean(request.name());

        if (!name.equalsIgnoreCase(point.getName())
                && sewaPointRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("There is already a sewa point called " + name);
        }
        point.setName(name);
        if (request.active() != null) {
            point.setActive(request.active());
        }
        return SewaPointResponse.from(sewaPointRepository.save(point));
    }

    @Transactional
    public void deleteSewaPoint(Long id) {
        requireSetupPermission();
        SewaPoint point = sewaPointRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Sewa point", id));
        if (sewadarRepository.countBySewaPointId(id) > 0) {
            point.setActive(false);
            sewaPointRepository.save(point);
            log.info("Sewa point {} deactivated (sewadars still hold it)", point.getName());
            return;
        }
        sewaPointRepository.delete(point);
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

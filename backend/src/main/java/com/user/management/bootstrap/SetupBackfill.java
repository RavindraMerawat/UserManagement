package com.user.management.bootstrap;

import com.user.management.entity.Area;
import com.user.management.entity.SatsangPoint;
import com.user.management.entity.Sewadar;
import com.user.management.repository.AreaRepository;
import com.user.management.repository.SatsangPointRepository;
import com.user.management.repository.SewadarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Seeds the Setup lists from the area and satsang point names already written on
 * sewadar records.
 *
 * <p>Area and Point used to be free text. Now they are picked from a managed list,
 * and without this a sewadar recorded as being in "Indore" would open with an empty
 * Area picker - and saving the form would quietly blank a value that was correct.
 * So on the first start after the change, every distinct name already in use becomes
 * a Setup entry under the zone it was used in.</p>
 *
 * <p>Runs on every start and is a no-op once the names exist, because it only adds
 * what is missing. It never renames or removes anything: names that differ only by
 * case or spelling stay as they were entered, and tidying those up is a decision for
 * whoever runs the office, on the Setup screen.</p>
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class SetupBackfill implements ApplicationRunner {

    private final SewadarRepository sewadarRepository;
    private final AreaRepository areaRepository;
    private final SatsangPointRepository pointRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int areasAdded = 0;
        int pointsAdded = 0;

        // Keyed by zone and lower-cased name, so the same area is not created twice
        // within one pass when two sewadars spell it differently in case.
        Map<String, Area> areasByKey = new LinkedHashMap<>();
        for (Area area : areaRepository.findAll()) {
            areasByKey.put(key(area.getZone().getId(), area.getName()), area);
        }

        for (Sewadar sewadar : sewadarRepository.findAll()) {
            if (sewadar.getZone() == null) {
                continue;
            }
            Long zoneId = sewadar.getZone().getId();

            String areaName = trimmed(sewadar.getArea());
            if (areaName == null) {
                continue;
            }
            Area area = areasByKey.get(key(zoneId, areaName));
            if (area == null) {
                area = areaRepository.save(Area.builder()
                        .name(areaName)
                        .zone(sewadar.getZone())
                        .active(true)
                        .build());
                areasByKey.put(key(zoneId, areaName), area);
                areasAdded++;
            }

            String pointName = trimmed(sewadar.getCenterPoint());
            if (pointName == null) {
                continue;
            }
            if (!pointRepository.existsByAreaIdAndNameIgnoreCase(area.getId(), pointName)) {
                pointRepository.save(SatsangPoint.builder()
                        .name(pointName)
                        .area(area)
                        .active(true)
                        .build());
                pointsAdded++;
            }
        }

        if (areasAdded > 0 || pointsAdded > 0) {
            log.info("Setup backfill: added {} area(s) and {} satsang point(s) from names "
                    + "already used on sewadar records", areasAdded, pointsAdded);
        }
    }

    private static String key(Long zoneId, String name) {
        return zoneId + "|" + name.toLowerCase(Locale.ROOT);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}

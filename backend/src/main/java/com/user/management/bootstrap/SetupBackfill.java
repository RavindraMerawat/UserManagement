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

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Seeds the Setup lists from the area and satsang point names already written on
 * sewadar records.
 *
 * <p>Area and Point used to be free text. Now they are picked from a managed list,
 * and without this a sewadar recorded as being in "Indore" would open with an empty
 * Area picker - and saving the form would quietly blank a value that was correct.
 * So on the first start after the change, every distinct name already in use becomes
 * a Setup entry.</p>
 *
 * <p>Both lists are flat: an area belongs to no zone and a point to no area, so a
 * name appears once however many zones use it.</p>
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

        // Lower-cased, so the same name is not created twice in one pass when two
        // sewadars spell it differently in case.
        Set<String> areas = new LinkedHashSet<>();
        areaRepository.findAll().forEach(a -> areas.add(key(a.getName())));
        Set<String> points = new LinkedHashSet<>();
        pointRepository.findAll().forEach(p -> points.add(key(p.getName())));

        for (Sewadar sewadar : sewadarRepository.findAll()) {
            String areaName = trimmed(sewadar.getArea());
            if (areaName != null && areas.add(key(areaName))) {
                areaRepository.save(Area.builder().name(areaName).active(true).build());
                areasAdded++;
            }

            String pointName = trimmed(sewadar.getCenterPoint());
            if (pointName != null && points.add(key(pointName))) {
                pointRepository.save(SatsangPoint.builder().name(pointName).active(true).build());
                pointsAdded++;
            }
        }

        if (areasAdded > 0 || pointsAdded > 0) {
            log.info("Setup backfill: added {} area(s) and {} satsang point(s) from names "
                    + "already used on sewadar records", areasAdded, pointsAdded);
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

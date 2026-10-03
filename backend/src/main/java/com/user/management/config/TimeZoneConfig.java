package com.user.management.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * The application runs on India Standard Time, wherever the machine thinks it is.
 *
 * <p>Attendance is kept as a {@code LocalDate} and a {@code LocalTime} - a day and a
 * clock reading, with no zone attached - because that is what the office writes on
 * the sheet. A zone-less time is only as good as the clock that produced it, and
 * {@code LocalTime.now()} reads the <b>JVM's default zone</b>. The production server
 * keeps its clock in UTC, so a sewadar checked in at 4:51 pm was recorded as 11:21,
 * five and a half hours out, on every screen and in every report.</p>
 *
 * <p>Setting the default here rather than on the server means the rule travels with
 * the application: a laptop in another zone, a container with no {@code TZ}, a server
 * rebuilt by someone else - all of them stamp IST. {@code app.time-zone} can override
 * it if this is ever run for an office somewhere else, which is the only reason it is
 * a property and not a constant.</p>
 */
@Slf4j
@Configuration
public class TimeZoneConfig {

    /** What the office means by "now". Changed only by moving the office. */
    public static final String DEFAULT_ZONE = "Asia/Kolkata";

    @Value("${app.time-zone:" + DEFAULT_ZONE + "}")
    private String zoneId;

    @PostConstruct
    public void applyTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        log.info("Clock set to {} - attendance times and today's date are read from it", zoneId);
    }
}

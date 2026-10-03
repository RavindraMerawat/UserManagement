package com.user.management;

import com.user.management.config.TimeZoneConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
public class UserManagementApplication {

    public static void main(String[] args) {
        /*
         * Before anything reads a clock, including the first line Spring logs.
         * TimeZoneConfig applies the same default from app.time-zone once the
         * context is up; this is here so the startup lines are in the office's time
         * as well, and so a jar run with no configuration at all is still on IST.
         */
        TimeZone.setDefault(TimeZone.getTimeZone(
                System.getenv().getOrDefault("APP_TIME_ZONE", TimeZoneConfig.DEFAULT_ZONE)));
        SpringApplication.run(UserManagementApplication.class, args);
    }
}

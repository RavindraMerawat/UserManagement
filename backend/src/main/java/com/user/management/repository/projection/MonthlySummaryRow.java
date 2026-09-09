package com.user.management.repository.projection;

/** Spring Data interface projection for the monthly attendance aggregation. */
public interface MonthlySummaryRow {

    Long getSewadarId();

    String getBadgeNumber();

    String getSewadarName();

    String getZoneName();

    String getDepartment();

    Long getTotalRecords();

    Long getPresentDays();

    Long getHalfDays();

    Long getLeaveDays();

    Long getAbsentDays();

    Long getRosterSewaDays();

    Long getConstructionSewaDays();

    Double getTotalHours();
}

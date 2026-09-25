package com.user.management.repository.projection;

/** Spring Data interface projection for the monthly attendance aggregation. */
public interface MonthlySummaryRow {

    /** For the age column on the PDF; null when the sewadar has no birth date. */
    java.time.LocalDate getBirthDate();

    Boolean getExempted();

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

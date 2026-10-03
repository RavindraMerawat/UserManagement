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

    String getArea();

    /**
     * The satsang point the sewadar belongs to.
     *
     * <p>This used to read the sewa point, which is a different list entirely - and
     * an empty one on the report ever since the sewa point came off the Add Sewadar
     * form. The column showed a dash against every name.</p>
     */
    String getSatsangPoint();

    String getDepartment();

    /**
     * The grouping inside the area - "1A", "ZONE 1A IC", "Sup-13". It is what the
     * zone hours sheet prints in its ZONE column.
     *
     * <p>Aliased groupingName rather than grouping because GROUPING is a word both
     * SQL and HQL reserve.</p>
     */
    String getGroupingName();

    /** The designation, which decides who sits at the top of the sheet. */
    String getDesignation();

    /** Permanent or Open: the sheet prints one table for each, per zone. */
    com.user.management.entity.SewadarStatus getStatus();

    Long getTotalRecords();

    Long getPresentDays();

    Long getHalfDays();

    Long getLeaveDays();

    Long getAbsentDays();

    Long getDailySewaDays();

    Long getConstructionSewaDays();

    Double getTotalHours();
}

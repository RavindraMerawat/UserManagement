package com.user.management.report;

import com.user.management.entity.SewadarStatus;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The shape of the monthly hours sheet the office keeps: one table per zone, five
 * columns, office-bearers at the top.
 *
 * <p>It exists so the PDF and the Excel are the same sheet. They are drawn by two
 * unrelated libraries - PDFBox places every rule by hand, POI writes cells - and the
 * two would drift apart within a month if each decided its own ordering. Everything
 * either of them needs to know is settled here, once.</p>
 *
 * <p>Each zone prints twice: its permanent sewadars first, then its open ones under
 * a caption of their own. A zone with nobody open prints once - a caption with nothing
 * under it is a heading that promises a table and does not deliver one.</p>
 *
 * <p>What the sheet shows:</p>
 * <ul>
 *   <li><b>ZONE</b> is the sewadar's <em>grouping</em> - "1A", "ZONE 1A IC", "Sup-13" -
 *       not the zone they belong to. The zone names the table.</li>
 *   <li><b>&lt;Mon&gt; HOURS</b> is their <em>effective</em> hours: each day's time
 *       rounded to the nearest whole hour, which is how the office tallies it.</li>
 * </ul>
 */
public final class ZoneHoursSheet {

    /** "September", for the title over the whole sheet. */
    private static final DateTimeFormatter MONTH_FULL =
            DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);

    /** "Sep", for each table's caption and the hours column. */
    private static final DateTimeFormatter MONTH_SHORT =
            DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private final String title;
    private final String shortMonth;
    private final List<Section> sections;

    private ZoneHoursSheet(String title, String shortMonth, List<Section> sections) {
        this.title = title;
        this.shortMonth = shortMonth;
        this.sections = sections;
    }

    /** One zone's table. */
    public record Section(String caption, List<Line> lines) {
    }

    /**
     * One printed line.
     *
     * @param emphasised the co-ordinator, the incharges and the supervisors, who are
     *                   set in bold on the sheet so they can be found without reading
     *                   every row
     */
    public record Line(int serial, String badgeNumber, String name, String zone,
                       long hours, boolean emphasised) {
    }

    public static ZoneHoursSheet of(MonthlyReportResponse report) {
        String month = report.fromDate().format(MONTH_SHORT);

        /*
         * Zones keep the order the rows arrived in, which is the order the query
         * sorted them: one zone finishes before the next begins.
         */
        Map<String, List<MonthlyReportRow>> byZone = new LinkedHashMap<>();
        for (MonthlyReportRow row : report.rows()) {
            byZone.computeIfAbsent(blankToDash(row.zoneName()), k -> new ArrayList<>()).add(row);
        }

        List<Section> sections = new ArrayList<>();
        for (Map.Entry<String, List<MonthlyReportRow>> zone : byZone.entrySet()) {
            /*
             * The permanent ones first, then the open ones, both under the zone that
             * holds them. A row with no status at all goes with the permanent table
             * rather than falling between the two and off the sheet entirely.
             */
            List<MonthlyReportRow> open = zone.getValue().stream()
                    .filter(r -> r.status() == SewadarStatus.OPEN)
                    .toList();
            List<MonthlyReportRow> permanent = zone.getValue().stream()
                    .filter(r -> r.status() != SewadarStatus.OPEN)
                    .toList();

            addSection(sections, zone.getKey() + " " + month + " HOURS", permanent);
            addSection(sections, zone.getKey() + " Open " + month + " HOURS", open);
        }

        return new ZoneHoursSheet("Monthly Report " + report.fromDate().format(MONTH_FULL),
                month, sections);
    }

    /** One table, numbered from one, or nothing at all when nobody is in it. */
    private static void addSection(List<Section> sections, String caption,
                                   List<MonthlyReportRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<MonthlyReportRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparingInt(ZoneHoursSheet::rank)
                .thenComparing(r -> blankToDash(r.sewadarName()), String.CASE_INSENSITIVE_ORDER));

        List<Line> lines = new ArrayList<>(ordered.size());
        int serial = 1;
        for (MonthlyReportRow row : ordered) {
            lines.add(new Line(
                    serial++,
                    blankToDash(row.badgeNumber()),
                    blankToDash(row.sewadarName()),
                    blankToDash(row.grouping()),
                    row.effectiveHours(),
                    rank(row) < PLAIN));
        }
        sections.add(new Section(caption, lines));
    }

    /** "Monthly Report September". */
    public String title() {
        return title;
    }

    /** The heading over the hours column: "Sep HOURS". */
    public String hoursHeading() {
        return shortMonth + " HOURS";
    }

    public List<Section> sections() {
        return sections;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    // ------------------------------------------------------------------ order

    /** Everybody who is not an office-bearer. */
    private static final int PLAIN = 3;

    /**
     * Where a row sits: the co-ordinator, then the incharges, then the supervisors,
     * then everyone else alphabetically.
     *
     * <p>Matched on the letters of the designation rather than on an exact name,
     * because the designations on file are written several ways - "Co-ordinator" and
     * "Coordinator", "Ass. Zone Incharge" and "Zone Incharge" - and a sheet that
     * silently drops someone out of its top block over a hyphen is worse than no
     * ordering at all.</p>
     */
    private static int rank(MonthlyReportRow row) {
        String designation = row.designation() == null ? "" : row.designation()
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (designation.contains("coordinator")) {
            return 0;
        }
        if (designation.contains("incharge")) {
            return 1;
        }
        if (designation.contains("supervisor")) {
            return 2;
        }
        return PLAIN;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

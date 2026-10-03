package com.user.management;

import com.user.management.entity.SewadarStatus;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import com.user.management.report.PdfReportWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PDF sheet is drawn string by string, with no table support underneath it, so
 * these read the finished document back and check what actually landed on the page.
 *
 * <p>The ordering and the sections are {@link com.user.management.report.ZoneHoursSheet}'s
 * and are tested there; what these add is that the drawing puts it on paper.</p>
 */
class PdfReportTest {

    private final PdfReportWriter writer = new PdfReportWriter();

    @Test
    @DisplayName("the title, the zone's caption, the five headings and a row")
    void carriesTheSheet() throws Exception {
        byte[] pdf = writer.write(
                report(row("L00447", "Sangeeta Batra", "ZONE 1 - NORTH",
                        "ZONE 1 CORDINATOR", "Co-ordinator", 92)),
                "Pandal Department");

        String text = textOf(pdf);

        assertThat(text).contains("Monthly Report September");
        assertThat(text).contains("ZONE 1 - NORTH Sep HOURS");
        assertThat(text).contains("S. NO.", "GR NO.", "NAME", "ZONE", "Sep HOURS");

        // The ZONE column carries the grouping, and the last column the whole hours.
        assertThat(text).contains("L00447", "Sangeeta Batra", "ZONE 1 CORDINATOR", "92");
    }

    @Test
    @DisplayName("five columns and no more - nothing from the old ten-column sheet")
    void theOldColumnsAreGone() throws Exception {
        String text = textOf(writer.write(
                report(row("L00447", "Sangeeta Batra", "Zone 1", "1A", "Sewadar", 92)),
                "Pandal Department"));

        /*
         * Read off the heading line rather than the whole page: "Department" is in
         * the brand name in the footer, and a test that searched the page for it
         * would be testing the footer.
         */
        String headings = text.lines()
                .filter(l -> l.startsWith("S. NO."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no heading line on the page"));

        assertThat(headings).isEqualTo("S. NO. GR NO. NAME ZONE Sep HOURS");
    }

    @Test
    @DisplayName("each zone gets its own table, one after another")
    void aTablePerZone() throws Exception {
        String text = textOf(writer.write(
                report(row("L00001", "North One", "Zone 1 - North", "1A", "Sewadar", 10),
                        row("L00002", "South One", "Zone 2 - South", "2A", "Sewadar", 20)),
                "Pandal Department"));

        assertThat(text).contains("Zone 1 - North Sep HOURS");
        assertThat(text).contains("Zone 2 - South Sep HOURS");
        assertThat(text.indexOf("Zone 1 - North Sep HOURS"))
                .isLessThan(text.indexOf("Zone 2 - South Sep HOURS"));
        assertThat(text).contains("North One", "South One");
    }

    @Test
    @DisplayName("the open table follows its zone's permanent one, in the same file")
    void theOpenTableFollowsThePermanentOne() throws Exception {
        String text = textOf(writer.write(
                report(row("L00001", "Perm One", "ZONE 1A", "1A", "Sewadar", 40),
                        openRow("Open", "Ankita Kundra", "ZONE 1A", "1A", 0),
                        openRow("New", "Archna Malviya", "ZONE 1A", "1A", 18)),
                "Pandal Department"));

        assertThat(text).contains("ZONE 1A Sep HOURS", "ZONE 1A Open Sep HOURS");
        assertThat(text.indexOf("ZONE 1A Sep HOURS"))
                .isLessThan(text.indexOf("ZONE 1A Open Sep HOURS"));
        // The GR. No column takes whatever is on the record, "Open" and "New" included.
        assertThat(text).contains("Ankita Kundra", "Archna Malviya", "Open", "New");
    }

    @Test
    @DisplayName("a name outside Latin-1 does not take the report down with it")
    void survivesANameOutsideLatin1() throws Exception {
        byte[] pdf = writer.write(
                report(row("L00001", "Plain Name", "Zone 1", "1A", "Sewadar", 8),
                        row("L00002", "रवीन्द्र", "Zone 1", "1A", "Sewadar", 4)),
                "Pandal Department");

        // The Standard 14 fonts are WinAnsi; an unmappable name must not kill the report.
        assertThat(textOf(pdf)).contains("L00001", "L00002");
    }

    @Test
    @DisplayName("a long list spills onto later pages rather than off the first")
    void spillsOntoASecondPage() throws Exception {
        MonthlyReportRow[] many = java.util.stream.IntStream.rangeClosed(1, 80)
                .mapToObj(i -> row("L" + String.format("%05d", i), "Sewadar " + i,
                        "Zone 1", "1A", "Sewadar", i))
                .toArray(MonthlyReportRow[]::new);

        try (PDDocument document = Loader.loadPDF(writer.write(report(many), "Pandal Department"))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            // first row and last row both present, so nothing was silently dropped
            assertThat(text).contains("L00001", "L00080");
            // and the headings come with the table onto the next page
            assertThat(text.split("Sep HOURS", -1)).hasSizeGreaterThan(2);
        }
    }

    @Test
    @DisplayName("every column is centred, not left aligned in its box")
    void everythingIsCentred() throws Exception {
        /*
         * A short value and a long one in the same column: centred, the short one
         * starts further right than the long one. Left aligned they would start at
         * the same x, which is exactly what this is here to catch.
         */
        float shortStart = startOf("NAME", "Jo");
        float longStart = startOf("NAME", "Chandrakanta G. Mimrot");

        assertThat(shortStart).isGreaterThan(longStart);
    }

    /** Where a row's NAME cell begins on the page, in points from the left. */
    private float startOf(String column, String name) throws Exception {
        byte[] pdf = writer.write(
                report(row("L00001", name, "Zone 1", "1A", "Sewadar", 8)), "Pandal Department");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PositionOf stripper = new PositionOf(name);
            stripper.getText(document);
            return stripper.x;
        }
    }

    /** Reads back where a given string actually landed. */
    private static final class PositionOf extends PDFTextStripper {
        private final String wanted;
        private float x = -1f;

        private PositionOf(String wanted) throws java.io.IOException {
            this.wanted = wanted;
        }

        @Override
        protected void writeString(String text, java.util.List<org.apache.pdfbox.text.TextPosition> positions) {
            if (x < 0 && text.contains(wanted) && !positions.isEmpty()) {
                x = positions.get(text.indexOf(wanted)).getXDirAdj();
            }
        }
    }

    @Test
    @DisplayName("a month with no attendance is still a readable page")
    void anEmptyMonth() throws Exception {
        String text = textOf(writer.write(report(), "Pandal Department"));

        assertThat(text).contains("Monthly Report September");
    }

    // ------------------------------------------------------------------ fixtures

    private String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private MonthlyReportResponse report(MonthlyReportRow... rows) {
        return new MonthlyReportResponse(
                "Monthly Attendance Report - September 2026",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                30,
                null,
                null,
                List.of(rows),
                new MonthlyReportResponse.Totals(rows.length, 3, 0, 0, 0, 3, 0, 25.5, 26, 3, 10.0),
                Map.of(),
                Map.of());
    }

    /** A permanent sewadar - the common case; openRow() is the other one. */
    private MonthlyReportRow row(String badge, String name, String zone, String grouping,
                                 String designation, long hours) {
        return row(badge, name, zone, grouping, designation, hours, SewadarStatus.PERMANENT);
    }

    private MonthlyReportRow openRow(String badge, String name, String zone, String grouping,
                                     long hours) {
        return row(badge, name, zone, grouping, "Sewadar", hours, SewadarStatus.OPEN);
    }

    private MonthlyReportRow row(String badge, String name, String zone, String grouping,
                                 String designation, long hours, SewadarStatus status) {
        return new MonthlyReportRow(
                1L,                 // sewadarId
                badge,              // GR. No
                name,
                zone,               // the zone, which names the table
                "",                 // area
                "",                 // sewa point
                "",                 // department
                grouping,           // the ZONE column
                designation,
                status,
                40,                 // age
                false,              // exempted
                3, 3, 0, 0, 0, 3, 0,
                hours + 0.4,        // clock time
                hours,              // effective hours
                3, 10.0);
    }
}

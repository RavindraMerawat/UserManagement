package com.user.management;

import com.user.management.entity.SewadarStatus;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import com.user.management.report.ZoneHoursSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape of the monthly hours sheet: one table per zone, office-bearers at the
 * top, the grouping in the ZONE column and the effective hours in the last one.
 *
 * <p>Asserted here rather than by reading the finished PDF and the finished workbook
 * twice over, because this is the one place that decides it - and a rule tested
 * through a renderer is a rule you have to test again for the other renderer.</p>
 */
class ZoneHoursSheetTest {

    @Test
    @DisplayName("one table per zone, captioned with the zone and the month")
    void oneTablePerZone() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "Aditi Pawar", "Zone 1 - North", "1A", "Sewadar", 13),
                row("L00002", "Komal Nandwani", "Zone 1 - North", "ZONE 1A IC", "Zone Incharge", 46),
                row("L00003", "Rekha Manwani", "Zone 2 - South", "2B", "Sewadar", 50)));

        assertThat(sheet.title()).isEqualTo("Monthly Report September");
        assertThat(sheet.hoursHeading()).isEqualTo("Sep HOURS");
        assertThat(sheet.sections()).extracting(ZoneHoursSheet.Section::caption)
                .containsExactly("Zone 1 - North Sep HOURS", "Zone 2 - South Sep HOURS");
        assertThat(sheet.sections().get(0).lines()).hasSize(2);
        assertThat(sheet.sections().get(1).lines()).hasSize(1);
    }

    @Test
    @DisplayName("a zone prints its permanent sewadars, then its open ones")
    void permanentThenOpen() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "Perm One", "ZONE 1A", "1A", "Sewadar", 40),
                openRow("Open", "Ankita Kundra", "ZONE 1A", "1A", 0),
                openRow("New", "Archna Malviya", "ZONE 1A", "1A", 18)));

        assertThat(sheet.sections()).extracting(ZoneHoursSheet.Section::caption)
                .containsExactly("ZONE 1A Sep HOURS", "ZONE 1A Open Sep HOURS");
        assertThat(sheet.sections().get(0).lines()).extracting(ZoneHoursSheet.Line::name)
                .containsExactly("Perm One");
        assertThat(sheet.sections().get(1).lines()).extracting(ZoneHoursSheet.Line::name)
                .containsExactly("Ankita Kundra", "Archna Malviya");
    }

    @Test
    @DisplayName("the open table numbers from one again")
    void theOpenTableNumbersFromOne() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "Perm One", "ZONE 1A", "1A", "Sewadar", 40),
                row("L00002", "Perm Two", "ZONE 1A", "1A", "Sewadar", 30),
                openRow("Open", "Ankita Kundra", "ZONE 1A", "1A", 0)));

        assertThat(sheet.sections().get(1).lines()).extracting(ZoneHoursSheet.Line::serial)
                .containsExactly(1);
    }

    @Test
    @DisplayName("a zone with nobody open prints once, with no empty heading")
    void noOpenTableWhenNobodyIsOpen() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "Perm One", "ZONE 1A", "1A", "Sewadar", 40)));

        assertThat(sheet.sections()).extracting(ZoneHoursSheet.Section::caption)
                .containsExactly("ZONE 1A Sep HOURS");
    }

    @Test
    @DisplayName("the two tables stay with their own zone, not gathered at the end")
    void eachZoneKeepsItsPair() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "North Perm", "Zone 1", "1A", "Sewadar", 40),
                openRow("Open", "North Open", "Zone 1", "1A", 5),
                row("L00002", "South Perm", "Zone 2", "2A", "Sewadar", 20),
                openRow("New", "South Open", "Zone 2", "2A", 7)));

        assertThat(sheet.sections()).extracting(ZoneHoursSheet.Section::caption)
                .containsExactly("Zone 1 Sep HOURS", "Zone 1 Open Sep HOURS",
                        "Zone 2 Sep HOURS", "Zone 2 Open Sep HOURS");
    }

    @Test
    @DisplayName("a row with no status at all is printed with the permanent ones")
    void anUnsetStatusIsNotLost() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "No Status", "Zone 1", "1A", "Sewadar", 9, null)));

        assertThat(sheet.sections()).hasSize(1);
        assertThat(sheet.sections().get(0).caption()).isEqualTo("Zone 1 Sep HOURS");
        assertThat(sheet.sections().get(0).lines()).extracting(ZoneHoursSheet.Line::name)
                .containsExactly("No Status");
    }

    @Test
    @DisplayName("the ZONE column is the grouping, not the zone the table is named for")
    void theZoneColumnIsTheGrouping() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "Aditi Pawar", "Zone 1 - North", "1A", "Sewadar", 13)));

        assertThat(sheet.sections().get(0).lines().get(0).zone()).isEqualTo("1A");
    }

    @Test
    @DisplayName("the hours are the effective ones - whole hours, as the office tallies them")
    void theHoursAreTheEffectiveOnes() {
        MonthlyReportRow row = row("L00001", "Aditi Pawar", "Zone 1", "1A", "Sewadar", 13);

        ZoneHoursSheet.Line line = ZoneHoursSheet.of(report(row)).sections().get(0).lines().get(0);

        // 13.4 hours of clock time rounds to the 13 that goes on the sheet.
        assertThat(row.totalHours()).isEqualTo(13.4);
        assertThat(line.hours()).isEqualTo(13);
    }

    @Test
    @DisplayName("the co-ordinator, then the incharges, then the supervisors, then the rest by name")
    void officeBearersComeFirst() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00010", "Zara Plain", "Zone 1", "1A", "Sewadar", 10),
                row("L00011", "Bindu Tikle", "Zone 1", "Sup-11", "Supervisor", 42),
                row("L00012", "Aditi Plain", "Zone 1", "1A", "Sewadar", 13),
                row("L00013", "Sangeeta Batra", "Zone 1", "ZONE 1 CORDINATOR", "Co-ordinator", 92),
                row("L00014", "Komal Nandwani", "Zone 1", "ZONE 1A IC", "Ass. Zone Incharge", 46)));

        assertThat(sheet.sections().get(0).lines()).extracting(ZoneHoursSheet.Line::name)
                .containsExactly("Sangeeta Batra", "Komal Nandwani", "Bindu Tikle",
                        "Aditi Plain", "Zara Plain");
    }

    @Test
    @DisplayName("those rows are the bold ones, and nobody else is")
    void officeBearersAreEmphasised() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00013", "Sangeeta Batra", "Zone 1", "ZONE 1 CORDINATOR", "Co-ordinator", 92),
                row("L00014", "Komal Nandwani", "Zone 1", "ZONE 1A IC", "Zone Incharge", 46),
                row("L00011", "Bindu Tikle", "Zone 1", "Sup-11", "Supervisor", 42),
                row("L00012", "Aditi Plain", "Zone 1", "1A", "Sewadar", 13)));

        assertThat(sheet.sections().get(0).lines()).extracting(ZoneHoursSheet.Line::emphasised)
                .containsExactly(true, true, true, false);
    }

    @Test
    @DisplayName("a designation spelt another way still counts - the hyphen does not decide it")
    void theSpellingOfTheDesignationDoesNotDecideIt() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00002", "Plain Sewadar", "Zone 1", "1A", "Sewadar", 5),
                row("L00001", "Written Without A Hyphen", "Zone 1", "1A", "Coordinator", 90)));

        assertThat(sheet.sections().get(0).lines().get(0).name())
                .isEqualTo("Written Without A Hyphen");
        assertThat(sheet.sections().get(0).lines().get(0).emphasised()).isTrue();
    }

    @Test
    @DisplayName("the serial number starts again in each zone")
    void theSerialStartsAgainPerZone() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "One", "Zone 1", "1A", "Sewadar", 1),
                row("L00002", "Two", "Zone 1", "1A", "Sewadar", 2),
                row("L00003", "Three", "Zone 2", "2A", "Sewadar", 3)));

        assertThat(sheet.sections().get(0).lines()).extracting(ZoneHoursSheet.Line::serial)
                .containsExactly(1, 2);
        assertThat(sheet.sections().get(1).lines()).extracting(ZoneHoursSheet.Line::serial)
                .containsExactly(1);
    }

    @Test
    @DisplayName("a sewadar with no grouping leaves a dash, not an empty box")
    void aMissingGroupingReads() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report(
                row("L00001", "No Grouping", "Zone 1", "", "Sewadar", 4)));

        assertThat(sheet.sections().get(0).lines().get(0).zone()).isEqualTo("-");
    }

    @Test
    @DisplayName("a month with nothing in it is an empty sheet, not a broken one")
    void anEmptyMonth() {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report());

        assertThat(sheet.isEmpty()).isTrue();
        assertThat(sheet.title()).isEqualTo("Monthly Report September");
    }

    // ------------------------------------------------------------------ fixtures

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

    /** Clock time a shade over the whole hour, so rounding is visible in the sheet. */
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
                status,        // decides where the row sits
                40,                 // age
                false,              // exempted
                3, 3, 0, 0, 0, 3, 0,
                hours + 0.4,        // clock time
                hours,              // effective hours
                3, 10.0);
    }
}

package com.user.management;

import com.user.management.entity.SewadarStatus;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import com.user.management.report.ReportExporter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Excel download is the same sheet the PDF draws, so these read the finished
 * workbook back and check the cells that landed in it.
 */
class ExcelReportTest {

    private final ReportExporter exporter = new ReportExporter();

    @Test
    @DisplayName("the title, the zone's caption and the five headings")
    void carriesTheSheet() throws Exception {
        List<String> lines = read(exporter.toExcel(report(
                row("L00447", "Sangeeta Batra", "ZONE 1 - NORTH", "ZONE 1 CORDINATOR",
                        "Co-ordinator", 92))));

        assertThat(lines.get(0)).isEqualTo("Monthly Report September");
        assertThat(lines).contains("ZONE 1 - NORTH Sep HOURS");
        assertThat(lines).contains("S. NO. | GR NO. | NAME | ZONE | Sep HOURS");
        assertThat(lines).contains("1 | L00447 | Sangeeta Batra | ZONE 1 CORDINATOR | 92");
    }

    @Test
    @DisplayName("each zone gets its own table, one after another")
    void aTablePerZone() throws Exception {
        List<String> lines = read(exporter.toExcel(report(
                row("L00001", "North One", "Zone 1 - North", "1A", "Sewadar", 10),
                row("L00002", "South One", "Zone 2 - South", "2A", "Sewadar", 20))));

        assertThat(lines.indexOf("Zone 1 - North Sep HOURS"))
                .isLessThan(lines.indexOf("Zone 2 - South Sep HOURS"));
        assertThat(lines).contains("1 | L00001 | North One | 1A | 10");
        assertThat(lines).contains("1 | L00002 | South One | 2A | 20");
    }

    @Test
    @DisplayName("the open table follows its zone's permanent one, in the same sheet")
    void theOpenTableFollowsThePermanentOne() throws Exception {
        List<String> lines = read(exporter.toExcel(report(
                row("L00001", "Perm One", "ZONE 1A", "1A", "Sewadar", 40),
                openRow("Open", "Ankita Kundra", "ZONE 1A", "1A", 0),
                openRow("New", "Archna Malviya", "ZONE 1A", "1A", 18))));

        assertThat(lines.indexOf("ZONE 1A Sep HOURS"))
                .isLessThan(lines.indexOf("ZONE 1A Open Sep HOURS"));
        assertThat(lines).contains("1 | Open | Ankita Kundra | 1A | 0");
        assertThat(lines).contains("2 | New | Archna Malviya | 1A | 18");
    }

    @Test
    @DisplayName("the office-bearers are the bold rows")
    void officeBearersAreBold() throws Exception {
        byte[] workbook = exporter.toExcel(report(
                row("L00013", "Sangeeta Batra", "Zone 1", "ZONE 1 CORDINATOR", "Co-ordinator", 92),
                row("L00012", "Aditi Plain", "Zone 1", "1A", "Sewadar", 13)));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(boldOfRowHolding(wb, sheet, "Sangeeta Batra")).isTrue();
            assertThat(boldOfRowHolding(wb, sheet, "Aditi Plain")).isFalse();
        }
    }

    @Test
    @DisplayName("nothing from the old ten-column sheet is left in the workbook")
    void theOldColumnsAreGone() throws Exception {
        byte[] workbook = exporter.toExcel(report(
                row("L00447", "Sangeeta Batra", "Zone 1", "1A", "Sewadar", 92)));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                assertThat(row.getLastCellNum()).isLessThanOrEqualTo((short) 5);
            }
        }
        assertThat(read(workbook)).noneMatch(l -> l.contains("Attendance %") || l.contains("Present"));
    }

    @Test
    @DisplayName("every cell is centred, heading and value alike")
    void everythingIsCentred() throws Exception {
        byte[] workbook = exporter.toExcel(report(
                row("L00013", "Sangeeta Batra", "Zone 1", "ZONE 1 CORDINATOR", "Co-ordinator", 92),
                row("L00012", "Aditi Plain", "Zone 1", "1A", "Sewadar", 13)));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (valueOf(cell).isEmpty()) {
                        continue;
                    }
                    assertThat(cell.getCellStyle().getAlignment())
                            .as("alignment of %s", valueOf(cell))
                            .isEqualTo(HorizontalAlignment.CENTER);
                }
            }
        }
    }

    @Test
    @DisplayName("the title is grey, so it names the month without competing with the tables")
    void theTitleIsGrey() throws Exception {
        byte[] workbook = exporter.toExcel(report(
                row("L00447", "Sangeeta Batra", "Zone 1", "1A", "Sewadar", 92)));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Cell title = wb.getSheetAt(0).getRow(0).getCell(0);
            assertThat(valueOf(title)).isEqualTo("Monthly Report September");
            assertThat(wb.getFontAt(title.getCellStyle().getFontIndex()).getColor())
                    .isEqualTo(IndexedColors.GREY_50_PERCENT.getIndex());
        }
    }

    @Test
    @DisplayName("the five columns fit the page it is printed on")
    void itFitsOnePageWide() throws Exception {
        byte[] workbook = exporter.toExcel(report(
                row("L00447", "Sangeeta Batra", "Zone 1", "1A", "Sewadar", 92)));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Sheet sheet = wb.getSheetAt(0);
            /*
             * Without this Excel puts the hours column - the one the sheet exists
             * for - alone on page two, which is only visible when somebody prints it.
             */
            assertThat(sheet.getFitToPage()).isTrue();
            assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
            assertThat(sheet.getPrintSetup().getFitHeight()).isEqualTo((short) 0);
        }
    }

    @Test
    @DisplayName("a month with nothing in it is still a workbook with a title")
    void anEmptyMonth() throws Exception {
        List<String> lines = read(exporter.toExcel(report()));

        assertThat(lines.get(0)).isEqualTo("Monthly Report September");
    }

    // ------------------------------------------------------------------ reading

    /** Each row as "a | b | c", so a test reads like the sheet does. */
    private List<String> read(byte[] workbook) throws Exception {
        List<String> lines = new ArrayList<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            for (Row row : wb.getSheetAt(0)) {
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    cells.add(valueOf(cell));
                }
                while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
                    cells.remove(cells.size() - 1);
                }
                lines.add(String.join(" | ", cells));
            }
        }
        return lines;
    }

    private boolean boldOfRowHolding(XSSFWorkbook wb, Sheet sheet, String name) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (name.equals(valueOf(cell))) {
                    return wb.getFontAt(cell.getCellStyle().getFontIndex()).getBold();
                }
            }
        }
        throw new AssertionError("no row holding " + name);
    }

    private String valueOf(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            default -> "";
        };
    }

    // ----------------------------------------------------------------- fixtures

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
                1L, badge, name, zone,
                "",                 // area
                "",                 // sewa point
                "",                 // department
                grouping,           // the ZONE column
                designation,
                status,
                40, false,
                3, 3, 0, 0, 0, 3, 0,
                hours + 0.4,        // clock time
                hours,              // effective hours
                3, 10.0);
    }
}

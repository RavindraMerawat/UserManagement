package com.user.management.report;

import com.user.management.AppInfo;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link MonthlyReportResponse} into a downloadable Excel workbook, a CSV
 * file, or an HTML/text summary for email and WhatsApp.
 */
@Slf4j
@Component
public class ReportExporter {

    /** The columns of the hours sheet. The last is named after the month it covers. */
    private static final String[] HEADERS = { "S. NO.", "GR NO.", "NAME", "ZONE", null };

    /** Wide enough for a full name without anyone having to drag a column out. */
    private static final int[] COLUMN_WIDTHS = { 2000, 3000, 7200, 5200, 3000 };

    /**
     * The monthly hours sheet: one table per zone, one after another.
     *
     * <p>The same sheet the PDF draws - {@link ZoneHoursSheet} decides the sections,
     * the order and who is set in bold, so the two downloads agree.</p>
     */
    public byte[] toExcel(MonthlyReportResponse report) {
        ZoneHoursSheet plan = ZoneHoursSheet.of(report);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Hours");

            CellStyle titleStyle = titleStyle(workbook);
            CellStyle captionStyle = captionStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle plainStyle = centredStyle(workbook);
            CellStyle emphasisStyle = emphasisStyle(workbook);

            int r = 0;

            Row titleRow = sheet.createRow(r++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(plan.title());
            titleCell.setCellStyle(titleStyle);
            titleRow.setHeightInPoints(24f);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            for (ZoneHoursSheet.Section section : plan.sections()) {
                r++;   // a blank line between zones

                Row captionRow = sheet.createRow(r);
                for (int c = 0; c < HEADERS.length; c++) {
                    captionRow.createCell(c).setCellStyle(captionStyle);
                }
                captionRow.getCell(0).setCellValue(section.caption());
                captionRow.setHeightInPoints(18f);
                sheet.addMergedRegion(new CellRangeAddress(r, r, 0, HEADERS.length - 1));
                r++;

                Row headerRow = sheet.createRow(r++);
                for (int c = 0; c < HEADERS.length; c++) {
                    Cell cell = headerRow.createCell(c);
                    cell.setCellValue(HEADERS[c] == null ? plan.hoursHeading() : HEADERS[c]);
                    cell.setCellStyle(headerStyle);
                }

                for (ZoneHoursSheet.Line line : section.lines()) {
                    // Every column centred, headings and values alike.
                    CellStyle style = line.emphasised() ? emphasisStyle : plainStyle;

                    Row dataRow = sheet.createRow(r++);
                    int c = 0;
                    writeCell(dataRow, c++, line.serial(), style);
                    writeCell(dataRow, c++, line.badgeNumber(), style);
                    writeCell(dataRow, c++, line.name(), style);
                    writeCell(dataRow, c++, line.zone(), style);
                    writeCell(dataRow, c, line.hours(), style);
                }
            }

            for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i]);
            }

            /*
             * This sheet is printed, not only read on screen. Without this the five
             * columns are a shade wider than a portrait page and Excel puts the hours
             * - the one column the sheet exists for - alone on page two.
             */
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);   // as many pages tall as it needs
            sheet.getPrintSetup().setLandscape(false);
            sheet.setRepeatingRows(org.apache.poi.ss.util.CellRangeAddress.valueOf("1:1"));

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the Excel report", e);
        }
    }

    public byte[] toCsv(MonthlyReportResponse report) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvEscape(report.title())).append('\n');
        sb.append("Period,").append(report.fromDate()).append(" to ").append(report.toDate()).append('\n');
        sb.append("Zone,").append(csvEscape(report.zoneName())).append('\n');
        sb.append("Sewa Type,").append(csvEscape(report.sewaTypeLabel())).append("\n\n");

        sb.append(String.join(",", HEADERS)).append('\n');
        int serial = 1;
        for (MonthlyReportRow row : report.rows()) {
            sb.append(serial++).append(',')
                    .append(csvEscape(row.badgeNumber())).append(',')
                    .append(csvEscape(row.sewadarName())).append(',')
                    .append(csvEscape(row.zoneName())).append(',')
                    .append(csvEscape(row.department())).append(',')
                    .append(row.presentDays()).append(',')
                    .append(row.halfDays()).append(',')
                    .append(row.leaveDays()).append(',')
                    .append(row.absentDays()).append(',')
                    .append(row.dailySewaDays()).append(',')
                    .append(row.constructionSewaDays()).append(',')
                    .append(row.totalHours()).append(',')
                    .append(row.effectiveDays()).append(',')
                    .append(row.attendancePercent()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** HTML body used for the email share. */
    public String toHtml(MonthlyReportResponse report, String note) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Segoe UI,Arial,sans-serif;color:#1f2937\">");
        sb.append("<h2 style=\"margin:0 0 4px;color:#b45309\">").append(escape(report.title())).append("</h2>");
        sb.append("<p style=\"margin:0 0 16px;font-size:13px;color:#6b7280\">")
                .append("Period: ").append(report.fromDate()).append(" to ").append(report.toDate())
                .append(" &nbsp;|&nbsp; Zone: ").append(escape(report.zoneName()))
                .append(" &nbsp;|&nbsp; Sewa Type: ").append(escape(report.sewaTypeLabel()))
                .append("</p>");

        if (note != null && !note.isBlank()) {
            sb.append("<p style=\"padding:10px 12px;background:#fef3c7;border-radius:6px\">")
                    .append(escape(note)).append("</p>");
        }

        MonthlyReportResponse.Totals t = report.totals();
        sb.append("<p style=\"font-size:14px\"><b>Summary:</b> ")
                .append(t.sewadarCount()).append(" sewadars &middot; ")
                .append(t.presentDays()).append(" present &middot; ")
                .append(t.halfDays()).append(" half day &middot; ")
                .append(t.leaveDays()).append(" leave &middot; ")
                .append(t.absentDays()).append(" absent &middot; ")
                .append(t.totalHours()).append(" hours &middot; avg ")
                .append(t.averageAttendancePercent()).append("%</p>");

        sb.append("<table cellspacing=\"0\" cellpadding=\"6\" ")
                .append("style=\"border-collapse:collapse;font-size:12px;width:100%\">");
        sb.append("<thead><tr style=\"background:#b45309;color:#fff\">");
        for (String header : HEADERS) {
            sb.append("<th style=\"border:1px solid #d1d5db;text-align:left\">")
                    .append(escape(header)).append("</th>");
        }
        sb.append("</tr></thead><tbody>");

        int serial = 1;
        // Keep the mail body reasonable; the full data set travels as the attachment.
        List<MonthlyReportRow> shown = report.rows().size() > 100
                ? report.rows().subList(0, 100)
                : report.rows();
        for (MonthlyReportRow row : shown) {
            String bg = serial % 2 == 0 ? "#f9fafb" : "#ffffff";
            sb.append("<tr style=\"background:").append(bg).append("\">");
            appendTd(sb, serial++);
            appendTd(sb, row.badgeNumber());
            appendTd(sb, row.sewadarName());
            appendTd(sb, row.zoneName());
            appendTd(sb, row.department());
            appendTd(sb, row.presentDays());
            appendTd(sb, row.halfDays());
            appendTd(sb, row.leaveDays());
            appendTd(sb, row.absentDays());
            appendTd(sb, row.dailySewaDays());
            appendTd(sb, row.constructionSewaDays());
            appendTd(sb, row.totalHours());
            appendTd(sb, row.effectiveDays());
            appendTd(sb, row.attendancePercent() + "%");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");

        if (report.rows().size() > shown.size()) {
            sb.append("<p style=\"font-size:12px;color:#6b7280\">Showing first ").append(shown.size())
                    .append(" of ").append(report.rows().size())
                    .append(" rows. The attached workbook has the complete data.</p>");
        }
        sb.append("<p style=\"font-size:11px;color:#9ca3af;margin-top:20px\">")
                .append("Generated by ").append(AppInfo.NAME).append(".</p>");
        sb.append("</div>");
        return sb.toString();
    }

    /** Compact plain-text summary used for the WhatsApp share. */
    public String toWhatsAppText(MonthlyReportResponse report, String note) {
        MonthlyReportResponse.Totals t = report.totals();
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(report.title()).append("*\n");
        sb.append("Period: ").append(report.fromDate()).append(" to ").append(report.toDate()).append('\n');
        sb.append("Zone: ").append(nullSafe(report.zoneName())).append('\n');
        sb.append("Sewa Type: ").append(nullSafe(report.sewaTypeLabel())).append("\n\n");

        if (note != null && !note.isBlank()) {
            sb.append(note).append("\n\n");
        }

        sb.append("*Summary*\n");
        sb.append("Sewadars: ").append(t.sewadarCount()).append('\n');
        sb.append("Present: ").append(t.presentDays()).append('\n');
        sb.append("Half day: ").append(t.halfDays()).append('\n');
        sb.append("Leave: ").append(t.leaveDays()).append('\n');
        sb.append("Absent: ").append(t.absentDays()).append('\n');
        sb.append("Daily sewa: ").append(t.dailySewaDays()).append('\n');
        sb.append("Construction sewa: ").append(t.constructionSewaDays()).append('\n');
        sb.append("Total hours: ").append(t.totalHours()).append('\n');
        sb.append("Average attendance: ").append(t.averageAttendancePercent()).append("%\n");

        if (!report.rows().isEmpty()) {
            sb.append("\n*Top 10 by attendance*\n");
            report.rows().stream()
                    .sorted((a, b) -> Double.compare(b.attendancePercent(), a.attendancePercent()))
                    .limit(10)
                    .forEach(row -> sb.append("- ").append(row.sewadarName())
                            .append(" (").append(row.badgeNumber()).append("): ")
                            .append(row.presentDays()).append("P/")
                            .append(row.absentDays()).append("A - ")
                            .append(row.attendancePercent()).append("%\n"));
        }
        return sb.toString();
    }

    public String fileName(MonthlyReportResponse report, String extension) {
        String base = report.title().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+$", "");
        return base + "." + extension;
    }

    public Map<String, String> summaryFields(MonthlyReportResponse report) {
        MonthlyReportResponse.Totals t = report.totals();
        return Map.of(
                "sewadars", String.valueOf(t.sewadarCount()),
                "present", String.valueOf(t.presentDays()),
                "absent", String.valueOf(t.absentDays()),
                "hours", String.valueOf(t.totalHours()));
    }

    // ---- cell helpers ----

    private void writeCell(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(value == null ? "" : value.toString());
        }
        cell.setCellStyle(style);
    }

    private void appendTd(StringBuilder sb, Object value) {
        sb.append("<td style=\"border:1px solid #e5e7eb\">")
                .append(escape(value == null ? "" : value.toString()))
                .append("</td>");
    }

    /** The zone's name across the whole table. */
    private CellStyle captionStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        border(style);
        return style;
    }

    /** Every cell on this sheet is centred, heading and value alike. */
    private CellStyle centredStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        border(style);
        return style;
    }

    /** The co-ordinator, the incharges and the supervisors, set in bold. */
    private CellStyle emphasisStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        border(style);
        return style;
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        // Grey, so the month names the sheet without competing with the tables.
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        border(style);
        return style;
    }

    private void border(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}

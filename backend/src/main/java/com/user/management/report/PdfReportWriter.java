package com.user.management.report;

import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The sewadar attendance sheet, as a PDF.
 *
 * <p>Six columns: <b>S.No, GR. No, Name, Age, Zone</b> and one column named after the
 * month the report covers, holding that sewadar's total hours in it. GR. No is the
 * badge number - the number people actually quote - and the month column is the whole
 * point of the sheet, which is why it carries the month's name rather than a generic
 * "Hours" heading.</p>
 *
 * <p>Drawn with PDFBox, which has no table support: every rule and every string is
 * placed by hand. That is why the column widths are declared once, up front, and
 * everything else measures against them. Text that would overflow its column is
 * clipped with an ellipsis rather than allowed to run into its neighbour.</p>
 */
@Component
public class PdfReportWriter {

    private static final PDRectangle PAGE = PDRectangle.A4;

    private static final float MARGIN = 36f;
    private static final float ROW_HEIGHT = 20f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float FONT_SIZE = 9.5f;
    private static final float HEADER_FONT_SIZE = 9.5f;
    private static final float CELL_PAD = 6f;

    /** S.No, GR. No, Name, Age, Zone, <month>. Sums to the printable width. */
    private static final float[] COLUMN_WIDTHS = { 42f, 80f, 165f, 38f, 130f, 68f };

    private static final String[] HEADINGS = { "S.No", "GR. No", "Name", "Age", "Zone", null };

    private static final DateTimeFormatter MONTH_COLUMN =
            DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter PERIOD =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public byte[] write(MonthlyReportResponse report, String brandName) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String monthColumn = report.fromDate().format(MONTH_COLUMN);
            List<MonthlyReportRow> rows = report.rows();

            float usableHeight = PAGE.getHeight() - (MARGIN * 2);
            // The first page gives up room to the title block; the rest do not.
            int firstPageRows = (int) ((usableHeight - 86f - HEADER_HEIGHT) / ROW_HEIGHT);
            int laterPageRows = (int) ((usableHeight - HEADER_HEIGHT) / ROW_HEIGHT);

            int index = 0;
            int pageNumber = 1;
            int totalPages = pageCount(rows.size(), firstPageRows, laterPageRows);

            do {
                boolean first = pageNumber == 1;
                int capacity = first ? firstPageRows : laterPageRows;
                int end = Math.min(index + capacity, rows.size());

                PDPage page = new PDPage(PAGE);
                document.addPage(page);
                try (PDPageContentStream c = new PDPageContentStream(document, page)) {
                    float y = PAGE.getHeight() - MARGIN;
                    if (first) {
                        y = drawTitle(c, report, brandName, y);
                    }
                    y = drawHeader(c, monthColumn, y);
                    for (int i = index; i < end; i++) {
                        y = drawRow(c, i + 1, rows.get(i), y);
                    }
                    if (end == rows.size()) {
                        drawTotals(c, report, y);
                    }
                    drawFooter(c, pageNumber, totalPages);
                }

                index = end;
                pageNumber++;
            } while (index < rows.size());

            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the PDF report", e);
        }
    }

    private int pageCount(int rowCount, int firstPageRows, int laterPageRows) {
        if (rowCount <= firstPageRows) {
            return 1;
        }
        int remaining = rowCount - firstPageRows;
        return 1 + (int) Math.ceil((double) remaining / laterPageRows);
    }

    // ------------------------------------------------------------------ blocks

    private float drawTitle(PDPageContentStream c, MonthlyReportResponse report,
                            String brandName, float top) throws IOException {
        float y = top - 14f;
        text(c, bold(), 15f, MARGIN, y, brandName);

        y -= 18f;
        text(c, bold(), 11.5f, MARGIN, y, report.title());

        y -= 14f;
        String period = report.fromDate().format(PERIOD) + " to " + report.toDate().format(PERIOD);
        text(c, plain(), 9f, MARGIN, y, period
                + "   |   Zone: " + blankToAll(report.zoneName())
                + "   |   Sewa: " + blankToAll(report.sewaTypeLabel()));

        y -= 14f;
        text(c, plain(), 9f, MARGIN, y,
                "Sewadars: " + report.totals().sewadarCount()
                        + "   |   Total hours: " + trim(report.totals().totalHours()));

        return y - 18f;
    }

    private float drawHeader(PDPageContentStream c, String monthColumn, float top) throws IOException {
        float y = top - HEADER_HEIGHT;

        c.setNonStrokingColor(0.93f, 0.95f, 0.98f);
        c.addRect(MARGIN, y, tableWidth(), HEADER_HEIGHT);
        c.fill();
        c.setNonStrokingColor(0f, 0f, 0f);

        float x = MARGIN;
        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            String heading = HEADINGS[i] == null ? monthColumn : HEADINGS[i];
            // The month column holds a number, so its heading is right aligned too.
            if (i == COLUMN_WIDTHS.length - 1) {
                rightText(c, bold(), HEADER_FONT_SIZE, x + COLUMN_WIDTHS[i] - CELL_PAD,
                        y + 7.5f, heading);
            } else {
                text(c, bold(), HEADER_FONT_SIZE, x + CELL_PAD, y + 7.5f,
                        fit(heading, COLUMN_WIDTHS[i], HEADER_FONT_SIZE, true));
            }
            x += COLUMN_WIDTHS[i];
        }

        line(c, MARGIN, y, MARGIN + tableWidth(), y);
        return y;
    }

    private float drawRow(PDPageContentStream c, int serial, MonthlyReportRow row, float top)
            throws IOException {
        float y = top - ROW_HEIGHT;
        float baseline = y + 6f;

        String[] values = {
                String.valueOf(serial),
                nullToDash(row.badgeNumber()),
                nullToDash(row.sewadarName()),
                row.age() == null ? "-" : String.valueOf(row.age()),
                nullToDash(row.zoneName()),
                trim(row.totalHours()),
        };

        float x = MARGIN;
        for (int i = 0; i < values.length; i++) {
            if (i == values.length - 1) {
                rightText(c, plain(), FONT_SIZE, x + COLUMN_WIDTHS[i] - CELL_PAD, baseline, values[i]);
            } else {
                text(c, plain(), FONT_SIZE, x + CELL_PAD, baseline,
                        fit(values[i], COLUMN_WIDTHS[i], FONT_SIZE, false));
            }
            x += COLUMN_WIDTHS[i];
        }

        c.setStrokingColor(0.88f, 0.90f, 0.94f);
        line(c, MARGIN, y, MARGIN + tableWidth(), y);
        c.setStrokingColor(0f, 0f, 0f);
        return y;
    }

    private void drawTotals(PDPageContentStream c, MonthlyReportResponse report, float top)
            throws IOException {
        float y = top - ROW_HEIGHT;
        float labelRight = MARGIN + tableWidth() - COLUMN_WIDTHS[5] - CELL_PAD;

        rightText(c, bold(), FONT_SIZE, labelRight, y + 6f, "Total");
        rightText(c, bold(), FONT_SIZE, MARGIN + tableWidth() - CELL_PAD, y + 6f,
                trim(report.totals().totalHours()));
        line(c, MARGIN, y, MARGIN + tableWidth(), y);
    }

    private void drawFooter(PDPageContentStream c, int page, int of) throws IOException {
        rightText(c, plain(), 8f, MARGIN + tableWidth(), MARGIN - 12f, "Page " + page + " of " + of);
    }

    // ----------------------------------------------------------------- drawing

    private void text(PDPageContentStream c, PDType1Font font, float size,
                      float x, float y, String value) throws IOException {
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(x, y);
        // Sanitised here, at the one place text actually reaches the page.
        c.showText(sanitise(value));
        c.endText();
    }

    private void rightText(PDPageContentStream c, PDType1Font font, float size,
                           float right, float y, String value) throws IOException {
        text(c, font, size, right - width(font, size, value), y, value);
    }

    private void line(PDPageContentStream c, float x1, float y1, float x2, float y2)
            throws IOException {
        c.setLineWidth(0.5f);
        c.moveTo(x1, y1);
        c.lineTo(x2, y2);
        c.stroke();
    }

    /** Trims a value to its column, with an ellipsis, so it cannot run into the next. */
    private String fit(String value, float columnWidth, float size, boolean boldFont) {
        PDType1Font font = boldFont ? bold() : plain();
        float room = columnWidth - (CELL_PAD * 2);
        if (width(font, size, value) <= room) {
            return value;
        }
        String cut = value;
        while (cut.length() > 1 && width(font, size, cut + "...") > room) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private float width(PDType1Font font, float size, String value) {
        try {
            return font.getStringWidth(sanitise(value)) / 1000f * size;
        } catch (IOException e) {
            // A width we cannot measure is better guessed than fatal to the report.
            return value.length() * size * 0.5f;
        }
    }

    private float tableWidth() {
        float total = 0f;
        for (float w : COLUMN_WIDTHS) {
            total += w;
        }
        return total;
    }

    // ------------------------------------------------------------------ values

    /**
     * The Standard 14 fonts are WinAnsi only, and a name carrying anything outside
     * it - a stray Devanagari character pasted into a record - would otherwise throw
     * while the page is being drawn and lose the whole report. Unmappable characters
     * are replaced rather than allowed to do that.
     */
    private String sanitise(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            sb.append(ch < 32 || ch > 255 ? '?' : ch);
        }
        return sb.toString();
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : sanitise(value);
    }

    private String blankToAll(String value) {
        return value == null || value.isBlank() ? "All" : sanitise(value);
    }

    /** 8.0 -> "8", 8.25 -> "8.25": no trailing zeros on a whole number of hours. */
    private String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    private PDType1Font plain() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private PDType1Font bold() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }
}

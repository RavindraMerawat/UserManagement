package com.user.management.report;

import com.user.management.model.MonthlyReportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The monthly hours sheet, as a PDF.
 *
 * <p>One table per zone, printed one after another under a single title. Five columns:
 * <b>S. NO., GR NO., NAME, ZONE</b> and the month's hours, every one of them centred.
 * ZONE holds the sewadar's grouping and the hours are the effective ones - {@link ZoneHoursSheet} says why, and
 * decides the order; this class only draws what it is given.</p>
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
    private static final float ROW_HEIGHT = 22f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float CAPTION_HEIGHT = 26f;
    private static final float TITLE_BLOCK = 46f;
    private static final float FONT_SIZE = 10f;
    private static final float CELL_PAD = 6f;

    /** The title's grey, as on the sheet the office keeps. */
    private static final float TITLE_GREY = 0.50f;

    /** S. NO., GR NO., NAME, ZONE, <Mon> HOURS. Sums to the printable width. */
    private static final float[] COLUMN_WIDTHS = { 52f, 76f, 185f, 130f, 80f };

    private static final String[] HEADINGS = { "S. NO.", "GR NO.", "NAME", "ZONE", null };

    public byte[] write(MonthlyReportResponse report, String brandName) {
        ZoneHoursSheet sheet = ZoneHoursSheet.of(report);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Page page = new Page(document);
            page.start();
            page.y = drawTitle(page.stream, sheet.title(), page.y);

            for (ZoneHoursSheet.Section section : sheet.sections()) {
                /*
                 * A caption with nothing under it is a promise the next page breaks,
                 * so a zone only starts on this page if its caption, its headings and
                 * one line of it will fit.
                 */
                if (page.remaining() < CAPTION_HEIGHT + HEADER_HEIGHT + ROW_HEIGHT) {
                    page.next();
                }
                page.y = drawCaption(page.stream, section.caption(), page.y);
                page.y = drawHeadings(page.stream, sheet.hoursHeading(), page.y);

                for (ZoneHoursSheet.Line line : section.lines()) {
                    if (page.remaining() < ROW_HEIGHT) {
                        page.next();
                        // The headings come with the table wherever it continues.
                        page.y = drawCaption(page.stream, section.caption(), page.y);
                        page.y = drawHeadings(page.stream, sheet.hoursHeading(), page.y);
                    }
                    page.y = drawLine(page.stream, line, page.y);
                }
                page.y -= 14f;   // air before the next zone
            }

            page.finish(brandName);
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the PDF report", e);
        }
    }

    /**
     * The page being drawn on, and the pen's height down it.
     *
     * <p>PDFBox will not let a closed stream be reopened, so starting a new page means
     * closing one stream and opening another. Keeping that in one place is what stops
     * a half-drawn table from leaking onto the wrong page.</p>
     */
    private final class Page {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;
        private int number;

        private Page(PDDocument document) {
            this.document = document;
        }

        private void start() throws IOException {
            PDPage page = new PDPage(PAGE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE.getHeight() - MARGIN;
            number++;
        }

        private void next() throws IOException {
            drawFooter(stream, number);
            stream.close();
            start();
        }

        private void finish(String brandName) throws IOException {
            drawFooter(stream, number);
            drawBrand(stream, brandName);
            stream.close();
        }

        /** How much room is left above the bottom margin. */
        private float remaining() {
            return y - MARGIN - 16f;
        }
    }

    // ------------------------------------------------------------------ blocks

    private float drawTitle(PDPageContentStream c, String title, float top) throws IOException {
        float y = top - 22f;
        // Grey, so the month names the page without competing with the tables under it.
        c.setNonStrokingColor(TITLE_GREY, TITLE_GREY, TITLE_GREY);
        centredText(c, bold(), 18f, MARGIN, tableWidth(), y, title);
        c.setNonStrokingColor(0f, 0f, 0f);
        return top - TITLE_BLOCK;
    }

    /** The zone's name across the whole table: "ZONE 1A Sep HOURS". */
    private float drawCaption(PDPageContentStream c, String caption, float top) throws IOException {
        float y = top - CAPTION_HEIGHT;
        box(c, MARGIN, y, tableWidth(), CAPTION_HEIGHT);
        centredText(c, bold(), 11.5f, MARGIN, tableWidth(), y + 8.5f, caption);
        return y;
    }

    private float drawHeadings(PDPageContentStream c, String hoursHeading, float top)
            throws IOException {
        float y = top - HEADER_HEIGHT;
        box(c, MARGIN, y, tableWidth(), HEADER_HEIGHT);

        float x = MARGIN;
        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            String heading = HEADINGS[i] == null ? hoursHeading : HEADINGS[i];
            cell(c, bold(), x, y + 7.5f, COLUMN_WIDTHS[i], heading);
            if (i > 0) {
                line(c, x, y, x, y + HEADER_HEIGHT);
            }
            x += COLUMN_WIDTHS[i];
        }
        return y;
    }

    private float drawLine(PDPageContentStream c, ZoneHoursSheet.Line line, float top)
            throws IOException {
        float y = top - ROW_HEIGHT;
        box(c, MARGIN, y, tableWidth(), ROW_HEIGHT);

        String[] values = {
                String.valueOf(line.serial()),
                line.badgeNumber(),
                line.name(),
                line.zone(),
                String.valueOf(line.hours()),
        };

        PDType1Font font = line.emphasised() ? bold() : plain();
        float x = MARGIN;
        for (int i = 0; i < values.length; i++) {
            cell(c, font, x, y + 7f, COLUMN_WIDTHS[i], values[i]);
            if (i > 0) {
                line(c, x, y, x, y + ROW_HEIGHT);
            }
            x += COLUMN_WIDTHS[i];
        }
        return y;
    }

    private void drawBrand(PDPageContentStream c, String brandName) throws IOException {
        text(c, plain(), 8f, MARGIN, MARGIN - 12f, brandName);
    }

    private void drawFooter(PDPageContentStream c, int page) throws IOException {
        rightText(c, plain(), 8f, MARGIN + tableWidth(), MARGIN - 12f, "Page " + page);
    }

    // ----------------------------------------------------------------- drawing

    /** One cell's text, centred in its column and clipped to it. */
    private void cell(PDPageContentStream c, PDType1Font font, float x, float baseline,
                      float columnWidth, String value) throws IOException {
        centredText(c, font, FONT_SIZE, x, columnWidth, baseline, fit(value, columnWidth, font));
    }

    private void box(PDPageContentStream c, float x, float y, float width, float height)
            throws IOException {
        c.setLineWidth(0.6f);
        c.addRect(x, y, width, height);
        c.stroke();
    }

    private void text(PDPageContentStream c, PDType1Font font, float size,
                      float x, float y, String value) throws IOException {
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(x, y);
        // Sanitised here, at the one place text actually reaches the page.
        c.showText(sanitise(value));
        c.endText();
    }

    private void centredText(PDPageContentStream c, PDType1Font font, float size,
                             float x, float width, float y, String value) throws IOException {
        text(c, font, size, x + ((width - width(font, size, value)) / 2f), y, value);
    }

    private void rightText(PDPageContentStream c, PDType1Font font, float size,
                           float right, float y, String value) throws IOException {
        text(c, font, size, right - width(font, size, value), y, value);
    }

    private void line(PDPageContentStream c, float x1, float y1, float x2, float y2)
            throws IOException {
        c.setLineWidth(0.6f);
        c.moveTo(x1, y1);
        c.lineTo(x2, y2);
        c.stroke();
    }

    /** Trims a value to its column, with an ellipsis, so it cannot run into the next. */
    private String fit(String value, float columnWidth, PDType1Font font) {
        float room = columnWidth - (CELL_PAD * 2);
        if (width(font, FONT_SIZE, value) <= room) {
            return value;
        }
        String cut = value;
        while (cut.length() > 1 && width(font, FONT_SIZE, cut + "...") > room) {
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

    private PDType1Font plain() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private PDType1Font bold() {
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    }
}

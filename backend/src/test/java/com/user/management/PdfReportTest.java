package com.user.management;

import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.MonthlyReportRow;
import com.user.management.report.PdfReportWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The PDF sheet is drawn string by string, with no table support underneath it, so
 * these read the finished document back and check what actually landed on the page.
 */
class PdfReportTest {

    private final PdfReportWriter writer = new PdfReportWriter();

    private MonthlyReportResponse report(List<MonthlyReportRow> rows) {
        return new MonthlyReportResponse(
                "Monthly Attendance Report - September 2026",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                30,
                null,
                null,
                rows,
                new MonthlyReportResponse.Totals(rows.size(), 3, 0, 0, 0, 3, 0, 25.5, 10.0),
                Map.of(),
                Map.of());
    }

    private MonthlyReportRow row(String badge, String name, Integer age, String zone, double hours) {
        return new MonthlyReportRow(1L, badge, name, zone, "", age, false,
                3, 3, 0, 0, 0, 3, 0, hours, 3, 10.0);
    }

    private String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void carriesTheSixColumnsAndNamesTheMonth() throws Exception {
        byte[] pdf = writer.write(
                report(List.of(row("B00123", "Ravindra", 38, "Zone 1 - North", 25.5))),
                "Pandal Office Management");

        String text = textOf(pdf);

        assertThat(text).contains("S.No", "GR. No", "Name", "Age", "Zone");
        // The last column is named for the month it reports, not "Hours".
        assertThat(text).contains("September");
        assertThat(text).doesNotContain("Total Hours");

        // and the row itself
        assertThat(text).contains("B00123", "Ravindra", "38", "Zone 1 - North", "25.5");
    }

    @Test
    void survivesAMissingBirthDateAndANameOutsideLatin1() throws Exception {
        byte[] pdf = writer.write(
                report(List.of(
                        row("B00001", "No Birth Date", null, "Zone 1", 8),
                        row("B00002", "रवीन्द्र", 40, "Zone 2", 4))),
                "Pandal Office Management");

        // The Standard 14 fonts are WinAnsi; an unmappable name must not kill the report.
        assertThat(textOf(pdf)).contains("B00001", "B00002");
    }

    @Test
    void spillsOntoASecondPageRatherThanOffTheFirst() throws Exception {
        List<MonthlyReportRow> many = java.util.stream.IntStream.rangeClosed(1, 80)
                .mapToObj(i -> row("B" + String.format("%05d", i), "Sewadar " + i, 30, "Zone 1", i))
                .toList();

        try (PDDocument document = Loader.loadPDF(writer.write(report(many), "Pandal Office Management"))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(document);
            // first row and last row both present, so nothing was silently dropped
            assertThat(text).contains("B00001", "B00080");
        }
    }
}

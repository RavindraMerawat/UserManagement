package com.user.management.controller;

import com.user.management.entity.SewaType;
import com.user.management.integration.NotificationService;
import com.user.management.model.MonthlyReportResponse;
import com.user.management.model.ShareReportRequest;
import com.user.management.model.ShareResult;
import com.user.management.report.ReportExporter;
import com.user.management.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@Tag(name = "5. Reports", description = """
        Monthly attendance, roster sewa and construction sewa reports, plus Excel and
        CSV download and sharing over email and WhatsApp. Every report is generated
        inside the caller's data scope, so a Sewadar only ever gets their own figures.
        """)
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;
    private final ReportExporter exporter;
    private final NotificationService notificationService;

    @Operation(summary = "Monthly attendance report")
    @GetMapping("/monthly")
    public MonthlyReportResponse monthly(
            @Parameter(description = "Four digit year, e.g. 2026") @RequestParam int year,
            @Parameter(description = "1 to 12") @RequestParam int month,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long sewadarId,
            @RequestParam(required = false) SewaType sewaType) {
        return reportService.monthly(year, month, zoneId, sewadarId, sewaType);
    }

    @Operation(summary = "Roster sewa report for a month")
    @GetMapping("/roster-sewa")
    public MonthlyReportResponse rosterSewa(@RequestParam int year,
                                            @RequestParam int month,
                                            @RequestParam(required = false) Long zoneId,
                                            @RequestParam(required = false) Long sewadarId) {
        return reportService.rosterSewa(year, month, zoneId, sewadarId);
    }

    @Operation(summary = "Construction sewa report for a month")
    @GetMapping("/construction-sewa")
    public MonthlyReportResponse constructionSewa(@RequestParam int year,
                                                  @RequestParam int month,
                                                  @RequestParam(required = false) Long zoneId,
                                                  @RequestParam(required = false) Long sewadarId) {
        return reportService.constructionSewa(year, month, zoneId, sewadarId);
    }

    @Operation(summary = "Attendance report over a custom date range")
    @GetMapping("/range")
    public MonthlyReportResponse range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long sewadarId,
            @RequestParam(required = false) SewaType sewaType) {
        return reportService.range(fromDate, toDate, zoneId, sewadarId, sewaType, null);
    }

    @Operation(summary = "Download a report as an Excel workbook")
    @GetMapping("/monthly/excel")
    public ResponseEntity<byte[]> monthlyExcel(@RequestParam int year,
                                               @RequestParam int month,
                                               @RequestParam(required = false) Long zoneId,
                                               @RequestParam(required = false) Long sewadarId,
                                               @RequestParam(required = false) SewaType sewaType) {
        MonthlyReportResponse report = reportService.monthly(year, month, zoneId, sewadarId, sewaType);
        return download(exporter.toExcel(report), exporter.fileName(report, "xlsx"), XLSX_MIME);
    }

    @Operation(summary = "Download a report as CSV")
    @GetMapping("/monthly/csv")
    public ResponseEntity<byte[]> monthlyCsv(@RequestParam int year,
                                             @RequestParam int month,
                                             @RequestParam(required = false) Long zoneId,
                                             @RequestParam(required = false) Long sewadarId,
                                             @RequestParam(required = false) SewaType sewaType) {
        MonthlyReportResponse report = reportService.monthly(year, month, zoneId, sewadarId, sewaType);
        return download(exporter.toCsv(report), exporter.fileName(report, "csv"), "text/csv");
    }

    @Operation(summary = "Download a custom range report as an Excel workbook")
    @GetMapping("/range/excel")
    public ResponseEntity<byte[]> rangeExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long sewadarId,
            @RequestParam(required = false) SewaType sewaType) {
        MonthlyReportResponse report = reportService.range(fromDate, toDate, zoneId, sewadarId, sewaType, null);
        return download(exporter.toExcel(report), exporter.fileName(report, "xlsx"), XLSX_MIME);
    }

    @Operation(summary = "Download a custom range report as CSV")
    @GetMapping("/range/csv")
    public ResponseEntity<byte[]> rangeCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long sewadarId,
            @RequestParam(required = false) SewaType sewaType) {
        MonthlyReportResponse report = reportService.range(fromDate, toDate, zoneId, sewadarId, sewaType, null);
        return download(exporter.toCsv(report), exporter.fileName(report, "csv"), "text/csv");
    }

    @Operation(summary = "Share a monthly report over email and/or WhatsApp",
            description = """
                    The email carries the report as an HTML table plus an Excel attachment;
                    the WhatsApp message carries a text summary with the top ten sewadars.
                    Both channels are off until they are configured on the server, in which
                    case the response comes back with a warning and the message is logged.
                    """)
    @PostMapping("/monthly/share")
    public ShareResult shareMonthly(@RequestParam int year,
                                    @RequestParam int month,
                                    @RequestParam(required = false) Long zoneId,
                                    @RequestParam(required = false) Long sewadarId,
                                    @RequestParam(required = false) SewaType sewaType,
                                    @Valid @RequestBody ShareReportRequest request) {
        MonthlyReportResponse report = reportService.monthly(year, month, zoneId, sewadarId, sewaType);
        return notificationService.shareReport(report, request);
    }

    @Operation(summary = "Share a custom range report over email and/or WhatsApp")
    @PostMapping("/range/share")
    public ShareResult shareRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Long sewadarId,
            @RequestParam(required = false) SewaType sewaType,
            @Valid @RequestBody ShareReportRequest request) {
        MonthlyReportResponse report = reportService.range(fromDate, toDate, zoneId, sewadarId, sewaType, null);
        return notificationService.shareReport(report, request);
    }

    @Operation(summary = "Current year and month", description = "Convenience default for the UI filters.")
    @GetMapping("/current-period")
    public YearMonth currentPeriod() {
        return YearMonth.now();
    }

    private ResponseEntity<byte[]> download(byte[] body, String fileName, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, org.springframework.http.HttpStatus.OK);
    }
}

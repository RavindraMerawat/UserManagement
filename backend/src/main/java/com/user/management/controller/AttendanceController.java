package com.user.management.controller;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;
import com.user.management.model.AttendanceRequest;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.BulkAttendanceRequest;
import com.user.management.model.BulkCheckInOutRequest;
import com.user.management.model.CheckInOutRequest;
import com.user.management.model.CheckInOutResult;
import com.user.management.model.SewadarLookupResponse;
import com.user.management.model.PageResponse;
import com.user.management.service.AttendanceService;
import com.user.management.service.CheckInOutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "4. Attendance", description = """
        Marking and viewing attendance for roster sewa, construction sewa and office
        sewa. Marking is open to Admin, Office Admin, Zone Incharge and Supervisor,
        each within their own zones. A Sewadar can only read their own attendance.
        """)
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CheckInOutService checkInOutService;

    @Operation(summary = "Search attendance within your access scope")
    @GetMapping
    public PageResponse<AttendanceResponse> search(
            @RequestParam(required = false) Long sewadarId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) SewaType sewaType,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "GR. No, name or mobile number - whichever is to hand")
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "attendanceDate").and(Sort.by("id")));
        return attendanceService.search(sewadarId, zoneId, sewaType, status, fromDate, toDate,
                query, pageable);
    }

    @Operation(summary = "My own attendance for a date range", description = "For a Sewadar login.")
    @GetMapping("/me")
    public List<AttendanceResponse> myAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return attendanceService.myAttendance(fromDate, toDate);
    }

    @Operation(summary = "Get one attendance entry")
    @GetMapping("/{id}")
    public AttendanceResponse get(@PathVariable Long id) {
        return attendanceService.get(id);
    }

    // ------------------------------------------------ mark attendance screen

    @Operation(summary = "Find sewadars by badge number, name, mobile or Aadhaar",
            description = """
                    Powers the search box on the Mark Attendance screen. Badge number and
                    Aadhaar match exactly so scanning a card lands on one person; name and
                    mobile match on a contains. Each hit carries the sewadar's status for
                    the given date so the screen knows whether to offer Check In or
                    Check Out. Always narrowed to the caller's zones.
                    """)
    @GetMapping("/lookup")
    public List<SewadarLookupResponse> lookup(
            @Parameter(description = "Badge number, name, mobile number or Aadhaar number")
            @RequestParam String query,
            @RequestParam(required = false) SewaType sewaType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
        return checkInOutService.lookup(query, sewaType, onDate);
    }

    @Operation(summary = "Today's check in / check out status for one sewadar")
    @GetMapping("/status/{sewadarId}")
    public SewadarLookupResponse status(
            @PathVariable Long sewadarId,
            @RequestParam(required = false) SewaType sewaType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
        return checkInOutService.status(sewadarId, sewaType, onDate);
    }

    @Operation(summary = "Check one sewadar in",
            description = "Sets the in time and marks the day present. Refused if they are "
                    + "already checked in, so the original arrival time is never overwritten.")
    @PostMapping("/check-in")
    public AttendanceResponse checkIn(@Valid @RequestBody CheckInOutRequest request) {
        return checkInOutService.checkIn(request);
    }

    @Operation(summary = "Check one sewadar out",
            description = "Sets the out time and derives the sewa hours from the in time.")
    @PostMapping("/check-out")
    public AttendanceResponse checkOut(@Valid @RequestBody CheckInOutRequest request) {
        return checkInOutService.checkOut(request);
    }

    @Operation(summary = "Check a group in at once",
            description = "For the zone wise screen. A sewadar who cannot be marked is "
                    + "reported as a skipped row rather than failing the whole batch.")
    @PostMapping("/bulk-check-in")
    public CheckInOutResult bulkCheckIn(@Valid @RequestBody BulkCheckInOutRequest request) {
        return checkInOutService.bulkCheckIn(request);
    }

    @Operation(summary = "Check a group out at once")
    @PostMapping("/bulk-check-out")
    public CheckInOutResult bulkCheckOut(@Valid @RequestBody BulkCheckInOutRequest request) {
        return checkInOutService.bulkCheckOut(request);
    }

    // ------------------------------------------------------- direct marking

    @Operation(summary = "Mark attendance for one sewadar",
            description = "Re-marking the same sewadar, date and sewa type updates the existing entry.")
    @PostMapping
    public ResponseEntity<AttendanceResponse> mark(@Valid @RequestBody AttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.mark(request));
    }

    @Operation(summary = "Mark a whole sewa sheet in one call")
    @PostMapping("/bulk")
    public ResponseEntity<List<AttendanceResponse>> markBulk(@Valid @RequestBody BulkAttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceService.markBulk(request));
    }

    @Operation(summary = "Update an attendance entry")
    @PutMapping("/{id}")
    public AttendanceResponse update(@PathVariable Long id, @Valid @RequestBody AttendanceRequest request) {
        return attendanceService.update(id, request);
    }

    @Operation(summary = "Delete an attendance entry", description = "Admin and Office Admin only.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attendanceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

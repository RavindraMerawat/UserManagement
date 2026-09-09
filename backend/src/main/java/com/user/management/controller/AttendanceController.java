package com.user.management.controller;

import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.SewaType;
import com.user.management.model.AttendanceRequest;
import com.user.management.model.AttendanceResponse;
import com.user.management.model.BulkAttendanceRequest;
import com.user.management.model.PageResponse;
import com.user.management.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Search attendance within your access scope")
    @GetMapping
    public PageResponse<AttendanceResponse> search(
            @RequestParam(required = false) Long sewadarId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) SewaType sewaType,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "attendanceDate").and(Sort.by("id")));
        return attendanceService.search(sewadarId, zoneId, sewaType, status, fromDate, toDate, pageable);
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

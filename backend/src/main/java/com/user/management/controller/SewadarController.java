package com.user.management.controller;

import com.user.management.model.PageResponse;
import com.user.management.model.BadgeSummaryResponse;
import com.user.management.model.SewadarRequest;
import com.user.management.model.SewadarResponse;
import com.user.management.model.TabCountsResponse;
import com.user.management.service.SewadarService;
import com.user.management.entity.AttendanceStatus;
import com.user.management.entity.Photo;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "3. Sewadar", description = """
        Sewadar master data. Results are always narrowed to the caller's role:
        a Sewadar sees only their own record, a Zone Incharge or Supervisor only their
        zones, and Admin / Office Admin / Office User see everything. Add, edit and
        delete are limited to Admin and Office Admin.
        """)
@RestController
@RequestMapping("/api/sewadars")
@RequiredArgsConstructor
public class SewadarController {

    private final SewadarService sewadarService;

    @Operation(summary = "Search sewadars within your access scope")
    @GetMapping
    public PageResponse<SewadarResponse> search(
            @Parameter(description = "Free text over name, badge number, mobile and department")
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.fromString(direction), sortBy));
        return sewadarService.search(query, zoneId, active, pageable);
    }

    @Operation(summary = "Counts for the list tabs",
            description = "All, active and inactive within your zones, for the tab strip.")
    @GetMapping("/counts")
    public TabCountsResponse counts() {
        return sewadarService.tabCounts();
    }

    @Operation(summary = "Sewadars behind a dashboard tile",
            description = """
                    The people a Home tile counted. Omit `status` for the Total tile;
                    pass PRESENT, LEAVE or ABSENT for a day tile. Narrowed to the
                    caller's zones exactly like every other sewadar read, so a
                    zone-scoped role only ever gets its own zones, and a Sewadar only
                    their own record.
                    """)
    @GetMapping("/by-status")
    public PageResponse<SewadarResponse> byStatus(
            @Parameter(description = "PRESENT, LEAVE or ABSENT. Omit for every active sewadar.")
            @RequestParam(required = false) AttendanceStatus status,
            @Parameter(description = "Defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return sewadarService.dashboardList(status, date,
                PageRequest.of(page, Math.min(size, 200), Sort.by("name")));
    }

    @Operation(summary = "Badge issue, receipt and pending counts within your access scope")
    @GetMapping("/badges/summary")
    public BadgeSummaryResponse badgeSummary() {
        return sewadarService.badgeSummary();
    }

    @Operation(summary = "Mark a badge as issued", description = "Admin and Office Admin only.")
    @PostMapping("/{id}/badge/issue")
    public SewadarResponse issueBadge(@PathVariable Long id) {
        return sewadarService.issueBadge(id);
    }

    @Operation(summary = "Mark an issued badge as received", description = "Admin and Office Admin only.")
    @PostMapping("/{id}/badge/receive")
    public SewadarResponse receiveBadge(@PathVariable Long id) {
        return sewadarService.receiveBadge(id);
    }

    @Operation(summary = "Active sewadars for the attendance sheet")
    @GetMapping("/for-attendance")
    public List<SewadarResponse> forAttendance(@RequestParam(required = false) Long zoneId) {
        return sewadarService.forAttendanceSheet(zoneId);
    }

    @Operation(summary = "My own sewadar profile", description = "For a Sewadar login.")
    @GetMapping("/me")
    public SewadarResponse me() {
        return sewadarService.me();
    }

    @Operation(summary = "Get one sewadar")
    @GetMapping("/{id}")
    public SewadarResponse get(@PathVariable Long id) {
        return sewadarService.get(id);
    }

    @Operation(summary = "Add a sewadar", description = "Admin and Office Admin only.")
    @PostMapping
    public ResponseEntity<SewadarResponse> create(@Valid @RequestBody SewadarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sewadarService.create(request));
    }

    @Operation(summary = "Update a sewadar", description = "Admin and Office Admin only.")
    @PutMapping("/{id}")
    public SewadarResponse update(@PathVariable Long id, @Valid @RequestBody SewadarRequest request) {
        return sewadarService.update(id, request);
    }

    @Operation(summary = "Delete a sewadar",
            description = "A sewadar with attendance history is deactivated instead of removed.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sewadarService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------- photo

    @Operation(summary = "Upload or replace the sewadar photo",
            description = "JPEG, PNG or WebP, 3 MB maximum. The magic bytes are checked, "
                    + "so a renamed file is rejected.")
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SewadarResponse uploadPhoto(@PathVariable Long id,
                                @RequestPart("file") MultipartFile file) {
        return sewadarService.uploadPhoto(id, file);
    }

    @Operation(summary = "Fetch the sewadar photo",
            description = "Returns the raw image. This endpoint is authenticated like every "
                    + "other, so the UI loads it with the bearer token rather than putting "
                    + "the URL straight into an img tag.")
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Photo photo = sewadarService.photo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(String.valueOf(photo.getUpdatedAt().toEpochMilli()))
                .body(photo.getData());
    }

    @Operation(summary = "Remove the sewadar photo")
    @DeleteMapping("/{id}/photo")
    public SewadarResponse deletePhoto(@PathVariable Long id) {
        return sewadarService.deletePhoto(id);
    }
}

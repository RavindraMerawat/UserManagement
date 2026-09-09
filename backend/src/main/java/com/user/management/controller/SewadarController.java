package com.user.management.controller;

import com.user.management.model.PageResponse;
import com.user.management.model.SewadarRequest;
import com.user.management.model.SewadarResponse;
import com.user.management.service.SewadarService;
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
}

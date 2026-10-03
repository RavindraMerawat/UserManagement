package com.user.management.controller;

import com.user.management.model.ConstructionSewaRequest;
import com.user.management.model.ConstructionSewaResponse;
import com.user.management.model.PageResponse;
import com.user.management.service.ConstructionSewaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "4. Construction Sewa", description = """
        Construction sewa, one entry per sewadar per day - the count is how many days
        a sewadar has, and the same day cannot be recorded twice. Always read one
        sewadar at a time, within your zones. Recording is Admin, Office Incharge and
        Office Sewadar; everyone else on the sewadar screens may only read.
        """)
@RestController
@RequestMapping("/api/construction-sewa")
@RequiredArgsConstructor
public class ConstructionSewaController {

    private final ConstructionSewaService service;

    @Operation(summary = "One sewadar's recorded days, newest first")
    @GetMapping("/sewadar/{sewadarId}")
    public PageResponse<ConstructionSewaResponse> forSewadar(
            @PathVariable Long sewadarId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return service.forSewadar(sewadarId, pageable);
    }

    @Operation(summary = "Record a day of construction sewa",
            description = "Refused if that sewadar already has that day recorded.")
    @PostMapping
    public ConstructionSewaResponse record(@Valid @RequestBody ConstructionSewaRequest request) {
        return service.record(request);
    }

    @Operation(summary = "Remove one recorded day")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.user.management.controller;

import com.user.management.entity.BadgeAction;
import com.user.management.entity.Gender;
import com.user.management.model.PageResponse;
import com.user.management.model.SeatingDaySummary;
import com.user.management.model.SeatingPersonResponse;
import com.user.management.model.WeeklySeatingSewaRequest;
import com.user.management.model.WeeklySeatingSewaResponse;
import com.user.management.service.WeeklySeatingSewaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "5. Weekly Seating Sewa", description = """
        Who sat on a given Sunday or Thursday and the token they held - shown on the
        screens as Badge No. Recording one also marks that day's attendance, in the
        same transaction. Recording is Admin, Office Incharge and Office Sewadar.
        """)
@RestController
@RequestMapping("/api/weekly-seating")
@RequiredArgsConstructor
public class WeeklySeatingSewaController {

    private final WeeklySeatingSewaService service;

    @Operation(summary = "One sewadar's seating days, newest first")
    @GetMapping("/sewadar/{sewadarId}")
    public PageResponse<WeeklySeatingSewaResponse> forSewadar(
            @PathVariable Long sewadarId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        return service.forSewadar(sewadarId, pageable);
    }

    @Operation(summary = "Record a seating sewa and its token",
            description = """
                    Refused if that sewadar is already seated on the day, if the token
                    is already with somebody else on it, or if the day does not match
                    the date. Marks the day's attendance as present at the same time.
                    """)
    @PostMapping
    public WeeklySeatingSewaResponse record(
            @Valid @RequestBody WeeklySeatingSewaRequest request) {
        return service.record(request);
    }

    @Operation(summary = "The day's issued and received counts, by gender")
    @GetMapping("/summary")
    public SeatingDaySummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.summarise(date);
    }

    @Operation(summary = "The people behind one of the day's counts")
    @GetMapping("/day")
    public PageResponse<SeatingPersonResponse> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam BadgeAction action,
            @RequestParam(required = false) Gender gender,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return service.dayMovements(date, action, gender,
                PageRequest.of(page, Math.min(size, 200)));
    }

    @Operation(summary = "Remove one seating record")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

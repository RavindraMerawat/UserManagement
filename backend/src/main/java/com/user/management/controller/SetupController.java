package com.user.management.controller;

import com.user.management.model.AreaRequest;
import com.user.management.model.AreaResponse;
import com.user.management.model.SatsangPointRequest;
import com.user.management.model.SatsangPointResponse;
import com.user.management.service.SetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * The Setup screen: the master lists a sewadar record is built from.
 *
 * <p>Zones live under {@code /api/zones} and are shown on the same screen; the two
 * levels beneath them are here. Reading follows the caller's zone scope; writing is
 * Admin and Office Admin, enforced in the service.</p>
 */
@Tag(name = "9. Setup", description = "Master data: areas and satsang points.")
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    // ------------------------------------------------------------------ areas

    @Operation(summary = "List areas",
            description = "Within your zones. Filter by zone, or by active, for the pickers.")
    @GetMapping("/areas")
    public List<AreaResponse> areas(
            @Parameter(description = "Only this zone's areas") @RequestParam(required = false) Long zoneId,
            @Parameter(description = "Only active, or only retired") @RequestParam(required = false) Boolean active) {
        return setupService.listAreas(zoneId, active);
    }

    @Operation(summary = "Add an area", description = "Admin and Office Admin only.")
    @PostMapping("/areas")
    public ResponseEntity<AreaResponse> createArea(@Valid @RequestBody AreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(setupService.createArea(request));
    }

    @Operation(summary = "Rename or move an area", description = "Admin and Office Admin only.")
    @PutMapping("/areas/{id}")
    public AreaResponse updateArea(@PathVariable Long id, @Valid @RequestBody AreaRequest request) {
        return setupService.updateArea(id, request);
    }

    @Operation(summary = "Remove an area",
            description = "An area that still has satsang points is deactivated rather than "
                    + "deleted, so records naming it keep resolving.")
    @DeleteMapping("/areas/{id}")
    public ResponseEntity<Void> deleteArea(@PathVariable Long id) {
        setupService.deleteArea(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------- satsang points

    @Operation(summary = "List satsang points",
            description = "Within your zones. Filter by area, or by zone, for the pickers.")
    @GetMapping("/points")
    public List<SatsangPointResponse> points(
            @Parameter(description = "Only this area's points") @RequestParam(required = false) Long areaId,
            @Parameter(description = "Only this zone's points") @RequestParam(required = false) Long zoneId,
            @Parameter(description = "Only active, or only retired") @RequestParam(required = false) Boolean active) {
        return setupService.listPoints(areaId, zoneId, active);
    }

    @Operation(summary = "Add a satsang point", description = "Admin and Office Admin only.")
    @PostMapping("/points")
    public ResponseEntity<SatsangPointResponse> createPoint(
            @Valid @RequestBody SatsangPointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(setupService.createPoint(request));
    }

    @Operation(summary = "Rename or move a satsang point", description = "Admin and Office Admin only.")
    @PutMapping("/points/{id}")
    public SatsangPointResponse updatePoint(@PathVariable Long id,
                                            @Valid @RequestBody SatsangPointRequest request) {
        return setupService.updatePoint(id, request);
    }

    @Operation(summary = "Remove a satsang point", description = "Admin and Office Admin only.")
    @DeleteMapping("/points/{id}")
    public ResponseEntity<Void> deletePoint(@PathVariable Long id) {
        setupService.deletePoint(id);
        return ResponseEntity.noContent().build();
    }
}

package com.user.management.controller;

import com.user.management.entity.RequestStatus;
import com.user.management.model.PageResponse;
import com.user.management.model.ZoneChangeCreateRequest;
import com.user.management.model.ZoneChangeRequestResponse;
import com.user.management.model.ZoneChangeReviewRequest;
import com.user.management.service.ZoneChangeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. Zone change requests", description = """
        A Sewadar raises a request for themselves; a Zone Incharge or Supervisor can
        raise one for a sewadar in their zone. Admin and Office Admin approve or
        reject, and an approval moves the sewadar to the new zone.
        """)
@RestController
@RequestMapping("/api/requests/zone-change")
@RequiredArgsConstructor
public class ZoneChangeRequestController {

    private final ZoneChangeRequestService requestService;

    @Operation(summary = "List zone change requests in your scope")
    @GetMapping
    public PageResponse<ZoneChangeRequestResponse> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return requestService.search(status, pageable);
    }

    @Operation(summary = "Get one request")
    @GetMapping("/{id}")
    public ZoneChangeRequestResponse get(@PathVariable Long id) {
        return requestService.get(id);
    }

    @Operation(summary = "Raise a zone change request")
    @PostMapping
    public ResponseEntity<ZoneChangeRequestResponse> create(
            @Valid @RequestBody ZoneChangeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(requestService.create(request));
    }

    @Operation(summary = "Approve or reject a request", description = "Admin and Office Admin only.")
    @PutMapping("/{id}/review")
    public ZoneChangeRequestResponse review(@PathVariable Long id,
                                            @Valid @RequestBody ZoneChangeReviewRequest review) {
        return requestService.review(id, review);
    }

    @Operation(summary = "Cancel a pending request you raised")
    @PutMapping("/{id}/cancel")
    public ZoneChangeRequestResponse cancel(@PathVariable Long id) {
        return requestService.cancel(id);
    }
}

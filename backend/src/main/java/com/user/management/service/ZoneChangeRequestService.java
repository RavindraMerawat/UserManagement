package com.user.management.service;

import com.user.management.entity.RequestStatus;
import com.user.management.entity.Sewadar;
import com.user.management.entity.Zone;
import com.user.management.entity.ZoneChangeRequest;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.ForbiddenException;
import com.user.management.exception.NotFoundException;
import com.user.management.integration.NotificationService;
import com.user.management.model.PageResponse;
import com.user.management.model.ZoneChangeCreateRequest;
import com.user.management.model.ZoneChangeRequestResponse;
import com.user.management.model.ZoneChangeReviewRequest;
import com.user.management.repository.SewadarRepository;
import com.user.management.repository.ZoneChangeRequestRepository;
import com.user.management.security.CurrentUserService;
import com.user.management.security.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Zone change requests. A sewadar can raise one for themselves; a zone incharge or
 * supervisor can raise one for a sewadar in their zone; ADMIN and OFFICE_ADMIN
 * approve or reject, and approval moves the sewadar to the new zone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneChangeRequestService {

    private final ZoneChangeRequestRepository requestRepository;
    private final SewadarRepository sewadarRepository;
    private final ZoneService zoneService;
    private final CurrentUserService currentUser;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<ZoneChangeRequestResponse> search(RequestStatus status, Pageable pageable) {
        DataScope scope = currentUser.scope();
        return PageResponse.of(
                requestRepository.search(status, scope.zoneIds(), scope.sewadarId(), pageable),
                ZoneChangeRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public ZoneChangeRequestResponse get(Long id) {
        return ZoneChangeRequestResponse.from(getInScope(id));
    }

    @Transactional
    public ZoneChangeRequestResponse create(ZoneChangeCreateRequest request) {
        Sewadar sewadar = resolveSubject(request.sewadarId());

        if (sewadar.getZone().getId().equals(request.toZoneId())) {
            throw new BadRequestException("The sewadar is already in " + sewadar.getZone().getName());
        }
        if (requestRepository.existsBySewadarIdAndStatus(sewadar.getId(), RequestStatus.PENDING)) {
            throw new BadRequestException("A zone change request for this sewadar is already pending");
        }
        Zone toZone = zoneService.getEntity(request.toZoneId());
        if (!toZone.isActive()) {
            throw new BadRequestException("Zone " + toZone.getName() + " is not active");
        }

        ZoneChangeRequest entity = ZoneChangeRequest.builder()
                .sewadar(sewadar)
                .fromZone(sewadar.getZone())
                .toZone(toZone)
                .reason(request.reason())
                .status(RequestStatus.PENDING)
                .requestedBy(currentUser.username())
                .build();

        ZoneChangeRequest saved = requestRepository.save(entity);
        notificationService.notifyZoneChangeRaised(saved);
        return ZoneChangeRequestResponse.from(saved);
    }

    @Transactional
    public ZoneChangeRequestResponse review(Long id, ZoneChangeReviewRequest review) {
        if (!currentUser.canReviewRequests()) {
            throw new ForbiddenException("Only Admin and Office Admin can approve or reject a zone change");
        }
        ZoneChangeRequest request = requestRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Zone change request", id));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("This request was already "
                    + request.getStatus().getDisplayName().toLowerCase());
        }
        if (review.decision() != RequestStatus.APPROVED && review.decision() != RequestStatus.REJECTED) {
            throw new BadRequestException("Decision must be APPROVED or REJECTED");
        }

        request.setStatus(review.decision());
        request.setReviewedBy(currentUser.username());
        request.setReviewedAt(Instant.now());
        request.setReviewRemarks(review.remarks());

        if (review.decision() == RequestStatus.APPROVED) {
            Sewadar sewadar = request.getSewadar();
            sewadar.setZone(request.getToZone());
            sewadarRepository.save(sewadar);
            log.info("Sewadar {} moved from {} to {} by {}", sewadar.getBadgeNumber(),
                    request.getFromZone().getCode(), request.getToZone().getCode(), currentUser.username());
        }

        ZoneChangeRequest saved = requestRepository.save(request);
        notificationService.notifyZoneChangeReviewed(saved);
        return ZoneChangeRequestResponse.from(saved);
    }

    /** The raiser (or an admin) may withdraw a request that is still pending. */
    @Transactional
    public ZoneChangeRequestResponse cancel(Long id) {
        ZoneChangeRequest request = getInScope(id);
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BadRequestException("Only a pending request can be cancelled");
        }
        boolean isRaiser = currentUser.username().equalsIgnoreCase(request.getRequestedBy());
        if (!isRaiser && !currentUser.canReviewRequests()) {
            throw new ForbiddenException("Only the person who raised this request can cancel it");
        }
        request.setStatus(RequestStatus.CANCELLED);
        request.setReviewedBy(currentUser.username());
        request.setReviewedAt(Instant.now());
        return ZoneChangeRequestResponse.from(requestRepository.save(request));
    }

    /**
     * Works out which sewadar the request is about. A SEWADAR login may only target
     * itself; every other role must name a sewadar inside its own scope.
     */
    private Sewadar resolveSubject(Long requestedSewadarId) {
        Long ownSewadarId = currentUser.principal().getSewadarId();
        if (ownSewadarId != null) {
            if (requestedSewadarId != null && !requestedSewadarId.equals(ownSewadarId)) {
                throw new ForbiddenException("You can only raise a zone change request for yourself");
            }
            return sewadarRepository.findById(ownSewadarId)
                    .orElseThrow(() -> NotFoundException.of("Sewadar", ownSewadarId));
        }
        if (requestedSewadarId == null) {
            throw new BadRequestException("Select the sewadar this request is for");
        }
        Sewadar sewadar = sewadarRepository.findById(requestedSewadarId)
                .orElseThrow(() -> NotFoundException.of("Sewadar", requestedSewadarId));
        currentUser.requireZoneAccess(sewadar.getZone().getId());
        return sewadar;
    }

    private ZoneChangeRequest getInScope(Long id) {
        ZoneChangeRequest request = requestRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Zone change request", id));
        DataScope scope = currentUser.scope();
        boolean visible = scope.allowsSewadar(request.getSewadar().getId())
                && (scope.zoneIds() == null
                    || scope.zoneIds().contains(request.getFromZone().getId())
                    || scope.zoneIds().contains(request.getToZone().getId()));
        if (!visible) {
            throw new ForbiddenException("You do not have access to this request");
        }
        return request;
    }
}

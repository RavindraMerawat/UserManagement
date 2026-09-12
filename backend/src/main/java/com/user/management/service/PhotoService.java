package com.user.management.service;

import com.user.management.entity.Photo;
import com.user.management.entity.PhotoOwnerType;
import com.user.management.exception.BadRequestException;
import com.user.management.exception.NotFoundException;
import com.user.management.repository.PhotoRepository;
import com.user.management.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/**
 * Stores and serves sewadar and user photos.
 *
 * <p>Callers are responsible for checking that the owner is inside the caller's data
 * scope before calling in here; this class only handles the image itself.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    /**
     * 3 MB is plenty for an ID photo and keeps the table small. Taken from the entity
     * so the check here and the column the bytes have to fit in cannot disagree.
     */
    private static final long MAX_BYTES = Photo.MAX_DATA_BYTES;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final PhotoRepository photoRepository;
    private final CurrentUserService currentUser;

    @Transactional
    public Instant store(PhotoOwnerType ownerType, Long ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose an image to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("The image must be 3 MB or smaller");
        }

        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BadRequestException("The image must be a JPEG, PNG or WebP file");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The uploaded image could not be read");
        }
        if (!looksLikeImage(bytes, contentType)) {
            throw new BadRequestException(
                    "That file is not a valid " + contentType.replace("image/", "").toUpperCase() + " image");
        }

        Instant now = Instant.now();
        Photo photo = photoRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> Photo.builder().ownerType(ownerType).ownerId(ownerId).build());

        photo.setData(bytes);
        photo.setContentType(contentType);
        photo.setSizeBytes(bytes.length);
        photo.setUpdatedAt(now);
        photo.setUpdatedBy(currentUser.username());
        photoRepository.save(photo);

        log.info("{} photo saved for {} {} ({} bytes)", contentType, ownerType, ownerId, bytes.length);
        return now;
    }

    @Transactional(readOnly = true)
    public Photo get(PhotoOwnerType ownerType, Long ownerId) {
        return photoRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> new NotFoundException("No photo on file for this record"));
    }

    @Transactional
    public void delete(PhotoOwnerType ownerType, Long ownerId) {
        photoRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    /**
     * Checks the magic bytes so a renamed file cannot be stored and later served with
     * an image content type the browser will trust.
     */
    private boolean looksLikeImage(byte[] b, String contentType) {
        if (b.length < 12) {
            return false;
        }
        return switch (contentType) {
            // JPEG: FF D8 FF
            case "image/jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            case "image/png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                    && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                    && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
            // WebP: "RIFF" .... "WEBP"
            case "image/webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }
}

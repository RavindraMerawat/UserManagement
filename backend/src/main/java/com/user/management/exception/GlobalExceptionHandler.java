package com.user.management.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleForbidden(Exception ex, HttpServletRequest req) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex,
                                                                    HttpServletRequest req) {
        return body(HttpStatus.UNAUTHORIZED, "Invalid username or password", req, null);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException ex, HttpServletRequest req) {
        return body(HttpStatus.UNAUTHORIZED, "This account has been disabled. Contact the office admin.", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        return body(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex,
                                                                HttpServletRequest req) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), cause);

        // "Data too long for column" is a column that cannot hold the value, not two
        // rows colliding. Reporting it as a conflict sent people looking for a
        // duplicate that was never there, and suggesting a retry could not help:
        // the same bytes fail the same way every time.
        if (isValueTooLong(cause)) {
            return body(HttpStatus.PAYLOAD_TOO_LARGE,
                    "That value is too large for the field it is stored in. If it is a photo, "
                            + "choose a smaller image; if it keeps happening for every image, the "
                            + "photos table needs its schema repair, which runs at startup.",
                    req, null);
        }
        return body(HttpStatus.CONFLICT,
                "This record conflicts with existing data (duplicate badge number, zone code or attendance entry)",
                req, null);
    }

    /** MySQL error 1406 / SQLSTATE 22001 - the value does not fit the column. */
    private boolean isValueTooLong(String cause) {
        if (cause == null) {
            return false;
        }
        String lower = cause.toLowerCase();
        return lower.contains("data too long") || lower.contains("value too long");
    }

    /**
     * An upload bigger than the servlet limit, or a multipart body the container
     * could not parse.
     *
     * <p>Both are thrown while the request is still being read, before any controller
     * runs, so without this they fell through to the catch-all and came back as
     * "Something went wrong" with a 500 - which tells whoever picked the file
     * nothing at all. The size limit in {@code PhotoService} handles everything
     * smaller and gives the exact figure; this is the backstop above it.</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest req) {
        log.warn("Upload rejected on {}: larger than the servlet limit", req.getRequestURI());
        return body(HttpStatus.PAYLOAD_TOO_LARGE,
                "That file is too large to upload. Choose an image of 3 MB or less.", req, null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex,
                                                               HttpServletRequest req) {
        log.warn("Could not read the upload on {}: {}", req.getRequestURI(), ex.getMessage());
        return body(HttpStatus.BAD_REQUEST,
                "The uploaded file could not be read. Choose the image again and retry.", req, null);
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegration(IntegrationException ex, HttpServletRequest req) {
        log.error("Integration failure on {}", req.getRequestURI(), ex);
        return body(HttpStatus.BAD_GATEWAY, ex.getMessage(), req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", req, null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status,
                                                     String message,
                                                     HttpServletRequest req,
                                                     Map<String, String> fieldErrors) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        payload.put("path", req.getRequestURI());
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            payload.put("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(payload);
    }
}

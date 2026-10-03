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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        return body(HttpStatus.CONFLICT, conflictMessage(cause), req, null);
    }

    /**
     * Which value already exists, in the words of the form it was typed on.
     *
     * <p>The message used to list every unique key in the schema and leave the
     * reader to guess - "duplicate badge number, zone code or attendance entry" was
     * reported once for an account whose email was blank and whose badge number was
     * not duplicated at all. The database says which constraint it was; this says it
     * back in the language of the screen.</p>
     */
    private String conflictMessage(String cause) {
        String lower = cause == null ? "" : cause.toLowerCase();
        if (lower.contains("uk_user_email")) {
            return "That email address is already on another account.";
        }
        if (lower.contains("uk_user_username")) {
            return "That username is already taken. Choose another.";
        }
        if (lower.contains("uk_sewadar_badge") || lower.contains("badgeno")) {
            return "That GR. No is already on the register.";
        }
        if (lower.contains("uk_sewadar_aadhar")) {
            return "That Aadhaar number is already on another sewadar.";
        }
        if (lower.contains("uk_zone_code")) {
            return "That zone code is already in use.";
        }
        if (lower.contains("uk_attendance") || lower.contains("attendance")) {
            return "That sewadar already has attendance for this day and sewa type.";
        }
        if (lower.contains("uk_area_name") || lower.contains("uk_point_name")) {
            return "That name is already on the list.";
        }
        return "Something in this record already exists elsewhere. Check the values that "
                + "have to be unique - username, email, GR. No, Aadhaar - and try again.";
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

    /**
     * A URL that matches no endpoint is a 404, not a fault in the server.
     *
     * <p>Without this it falls through to the catch-all below and comes back as
     * "Something went wrong" with a stack trace in the log - which reads like a bug
     * in the application when it is really a request for something that is not
     * there, most often a screen calling an endpoint an older build did not have.</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoRoute(NoResourceFoundException ex,
                                                             HttpServletRequest req) {
        log.warn("No endpoint for {} {}", req.getMethod(), req.getRequestURI());
        return body(HttpStatus.NOT_FOUND,
                "No endpoint at " + req.getRequestURI()
                        + ". If this screen is newer than the server, restart the server.",
                req, null);
    }

    /**
     * A path or query value of the wrong type is the caller's mistake, so it is a 400
     * that names the parameter rather than a 500 that names nothing.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                  HttpServletRequest req) {
        log.warn("Bad value for {} on {}: {}", ex.getName(), req.getRequestURI(), ex.getValue());
        return body(HttpStatus.BAD_REQUEST,
                "\"" + ex.getValue() + "\" is not a valid " + ex.getName(), req, null);
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

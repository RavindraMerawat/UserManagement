package com.user.management.controller;

import com.user.management.entity.Role;
import com.user.management.model.CreateUserRequest;
import com.user.management.model.PageResponse;
import com.user.management.model.UpdateUserRequest;
import com.user.management.model.TabCountsResponse;
import com.user.management.model.UserResponse;
import com.user.management.service.UserService;
import com.user.management.entity.Photo;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "7. User accounts", description = "Login account administration. Admin only.")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Search login accounts")
    @GetMapping
    public PageResponse<UserResponse> search(@RequestParam(required = false) String query,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(required = false) Boolean enabled,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("username"));
        return userService.search(query, role, enabled, pageable);
    }

    @Operation(summary = "Get one account")
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @Operation(summary = "Create a login account",
            description = "Zone Incharge and Supervisor accounts need at least one zone; "
                    + "a Sewadar account needs a sewadar record to link.")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @Operation(summary = "Update an account, its role, its zones or its password")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @Operation(summary = "Delete an account")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------- photo

    @Operation(summary = "Counts for the list tabs",
            description = "All, active and inactive, so the tab strip can show its numbers.")
    @GetMapping("/counts")
    public TabCountsResponse counts() {
        return userService.tabCounts();
    }

    @Operation(summary = "Upload or replace the account photo",
            description = "JPEG, PNG or WebP, 3 MB maximum. The magic bytes are checked, "
                    + "so a renamed file is rejected.")
    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse uploadPhoto(@PathVariable Long id,
                                @RequestPart("file") MultipartFile file) {
        return userService.uploadPhoto(id, file);
    }

    @Operation(summary = "Fetch the account photo",
            description = "Returns the raw image. This endpoint is authenticated like every "
                    + "other, so the UI loads it with the bearer token rather than putting "
                    + "the URL straight into an img tag.")
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Photo photo = userService.photo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(String.valueOf(photo.getUpdatedAt().toEpochMilli()))
                .body(photo.getData());
    }

    @Operation(summary = "Remove the account photo")
    @DeleteMapping("/{id}/photo")
    public UserResponse deletePhoto(@PathVariable Long id) {
        return userService.deletePhoto(id);
    }
}

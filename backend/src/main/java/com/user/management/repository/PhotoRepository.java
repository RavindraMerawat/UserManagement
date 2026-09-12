package com.user.management.repository;

import com.user.management.entity.Photo;
import com.user.management.entity.PhotoOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    Optional<Photo> findByOwnerTypeAndOwnerId(PhotoOwnerType ownerType, Long ownerId);

    void deleteByOwnerTypeAndOwnerId(PhotoOwnerType ownerType, Long ownerId);

    boolean existsByOwnerTypeAndOwnerId(PhotoOwnerType ownerType, Long ownerId);
}

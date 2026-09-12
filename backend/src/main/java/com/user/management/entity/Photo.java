package com.user.management.entity;

import jakarta.persistence.*;
import lombok.*;


/**
 * Image bytes for a sewadar or a login account.
 *
 * <p>Deliberately its own table rather than a column on {@code sewadars} or
 * {@code users}: a LOB on the owning entity is loaded by every list query, so a
 * 200 row page would drag 200 photos into memory. Keeping the bytes here means the
 * grid queries stay cheap and photos are fetched one at a time by the image
 * endpoint. The owner keeps only a {@code photo_updated_at} timestamp.</p>
 */
@Entity
@Table(name = "photos",
        uniqueConstraints = @UniqueConstraint(name = "uk_photo_owner",
                columnNames = {"ownerType", "ownerId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Photo extends Auditable {

    /**
     * Largest image the table accepts, and the size the {@code data} column is
     * generated from. Hibernate picks the MySQL blob variant from this length, so
     * leaving it at the 255 byte default produced a {@code tinyblob} that no real
     * photo fits in. 3 MB lands on {@code mediumblob}.
     *
     * <p>{@code PhotoService} rejects anything larger before it reaches the database,
     * so the two limits are this one constant rather than two that can drift apart.</p>
     */
    public static final int MAX_DATA_BYTES = 3 * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownerType", nullable = false, length = 20)
    private PhotoOwnerType ownerType;

    @Column(name = "ownerId", nullable = false)
    private Long ownerId;

    @Lob
    @Column(name = "data", nullable = false, length = MAX_DATA_BYTES)
    private byte[] data;

    @Column(name = "contentType", nullable = false, length = 100)
    private String contentType;

    @Column(name = "sizeBytes", nullable = false)
    private long sizeBytes;

    /*
     * createdAt, createdBy, updatedAt and updatedBy come from Auditable, so this
     * table ends with the same four columns as every other one. PhotoService still
     * sets the update stamp explicitly, because the owning sewadar or account needs
     * the same value written to its photoUpdatedAt and the auditing listener does
     * not run until flush.
     */
}

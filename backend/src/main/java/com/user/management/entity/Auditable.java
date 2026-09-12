package com.user.management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
/*
 * The four audit columns every table carries. They are written LAST in the DDL of
 * every table on purpose: a row should read as its own facts first, with "who touched
 * it and when" as a footer. Hibernate cannot control column order - the schema script
 * does.
 */
public abstract class Auditable {

    @CreatedDate
    @Column(name = "createdAt", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updatedAt")
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "createdBy", updatable = false, length = 60)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updatedBy", length = 60)
    private String updatedBy;
}

package com.merchtyl.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID>, JpaSpecificationExecutor<AuditRecord> {
    @org.springframework.data.jpa.repository.Query("select u.tenantId from User u where u.id = :id")
    java.util.Optional<java.util.UUID> tenantIdForUser(@org.springframework.data.repository.query.Param("id") java.util.UUID id);
    @org.springframework.data.jpa.repository.Query("select s.tenantId from Store s where s.id = :id")
    java.util.Optional<java.util.UUID> tenantIdForStore(@org.springframework.data.repository.query.Param("id") java.util.UUID id);
}

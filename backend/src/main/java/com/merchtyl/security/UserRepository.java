package com.merchtyl.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    List<User> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    List<User> findByTestProvisionedTrue();

    List<User> findByTenantIdAndTestProvisionedTrue(UUID tenantId);

    List<User> findByTestProvisionedTrueAndEmailContainingIgnoreCase(String emailPattern);
}

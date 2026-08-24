package com.merchtyl.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductCapabilityAssignmentRepository extends JpaRepository<ProductCapabilityAssignment, UUID> {
}

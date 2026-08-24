package com.merchtyl.refunds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundItemTaxRepository extends JpaRepository<RefundItemTax, UUID> {
}

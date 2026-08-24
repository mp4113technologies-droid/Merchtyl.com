package com.merchtyl.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID>, JpaSpecificationExecutor<StockAdjustment> {
}

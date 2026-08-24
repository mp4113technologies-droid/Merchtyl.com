package com.merchtyl.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SaleAdjustmentRepository extends JpaRepository<SaleAdjustment, UUID> {}

package com.merchtyl.refunds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundPaymentRepository extends JpaRepository<RefundPayment, UUID> {
    @Query("""
            select coalesce(sum(payment.amount), 0)
            from RefundPayment payment
            where payment.originalPayment.id = :originalPaymentId
            """)
    BigDecimal refundedAmountForOriginalPayment(@Param("originalPaymentId") UUID originalPaymentId);
}

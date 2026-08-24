package com.merchtyl.lottery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.UUID;

public interface LotteryPayoutApprovalRepository extends JpaRepository<LotteryPayoutApproval, UUID>, JpaSpecificationExecutor<LotteryPayoutApproval> {
    @Override
    @EntityGraph(attributePaths = {
            "payout", "payout.operator", "payout.store", "payout.register", "payout.cashier", "approvedBy"
    })
    List<LotteryPayoutApproval> findAll(Specification<LotteryPayoutApproval> specification, Sort sort);
}

package com.merchtyl.lottery;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LotteryPosActivityRepository extends JpaRepository<LotteryPosActivity, UUID> {
    @EntityGraph(attributePaths = {"store", "register", "registerSession", "cashier"})
    List<LotteryPosActivity> findByStore_IdAndBusinessDateOrderByOccurredAtAscIdAsc(UUID storeId, LocalDate businessDate);
    Optional<LotteryPosActivity> findByOperationId(UUID operationId);
}

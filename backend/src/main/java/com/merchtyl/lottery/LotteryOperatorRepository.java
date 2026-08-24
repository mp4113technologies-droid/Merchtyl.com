package com.merchtyl.lottery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.UUID;

public interface LotteryOperatorRepository extends JpaRepository<LotteryOperator, UUID>, JpaSpecificationExecutor<LotteryOperator> {
    @Override
    @EntityGraph(attributePaths = {"jurisdiction"})
    Page<LotteryOperator> findAll(Specification<LotteryOperator> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"jurisdiction"})
    Optional<LotteryOperator> findById(UUID id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}

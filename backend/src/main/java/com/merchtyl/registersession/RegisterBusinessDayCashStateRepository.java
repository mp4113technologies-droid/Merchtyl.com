package com.merchtyl.registersession;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegisterBusinessDayCashStateRepository extends JpaRepository<RegisterBusinessDayCashState, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"store", "register", "businessDay"})
    @Query("select state from RegisterBusinessDayCashState state where state.businessDay.id = :businessDayId and state.register.id = :registerId")
    Optional<RegisterBusinessDayCashState> findForUpdate(@Param("businessDayId") UUID businessDayId,
                                                         @Param("registerId") UUID registerId);

    @EntityGraph(attributePaths = {"store", "register", "businessDay"})
    List<RegisterBusinessDayCashState> findAllByBusinessDay_Id(UUID businessDayId);

    @EntityGraph(attributePaths = {"store", "register", "businessDay"})
    List<RegisterBusinessDayCashState> findAllByBusinessDay_IdIn(java.util.Collection<UUID> businessDayIds);
}

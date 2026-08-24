package com.merchtyl.registersession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RegisterSessionOperatorHistoryRepository extends JpaRepository<RegisterSessionOperatorHistory, UUID> {
}

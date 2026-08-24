package com.merchtyl.security;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserRegisterAssignmentRepository extends JpaRepository<UserRegisterAssignment, UUID> {
    @EntityGraph(attributePaths = {"register"})
    List<UserRegisterAssignment> findByUser(User user);

    @EntityGraph(attributePaths = {"register"})
    List<UserRegisterAssignment> findByUserIn(Collection<User> users);

    boolean existsByUserAndRegister_Id(User user, UUID registerId);

    void deleteByUser(User user);
}

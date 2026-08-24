package com.merchtyl.security;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    @EntityGraph(attributePaths = {"role"})
    List<UserRole> findByUser(User user);

    @EntityGraph(attributePaths = {"role"})
    List<UserRole> findByUserIn(Collection<User> users);

    Optional<UserRole> findByUserAndRole(User user, Role role);

    boolean existsByUserAndRole(User user, Role role);

    void deleteByUser(User user);
}

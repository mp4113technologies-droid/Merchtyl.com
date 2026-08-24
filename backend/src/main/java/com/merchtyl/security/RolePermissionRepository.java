package com.merchtyl.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    List<RolePermission> findByRole(Role role);

    Optional<RolePermission> findByRoleAndPermission(Role role, Permission permission);

    @Query("""
            select distinct rolePermission.permission.code
            from RolePermission rolePermission
            join UserRole userRole on userRole.role = rolePermission.role
            where userRole.user = :user
            order by rolePermission.permission.code
            """)
    List<String> findPermissionCodesByUser(@Param("user") User user);

    boolean existsByRoleAndPermission(Role role, Permission permission);
}

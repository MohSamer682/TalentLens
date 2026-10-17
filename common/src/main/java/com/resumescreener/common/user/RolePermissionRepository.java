package com.resumescreener.common.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.Key> {
    List<RolePermission> findByRole(Role role);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.role = :role")
    void deleteByRole(Role role);
}

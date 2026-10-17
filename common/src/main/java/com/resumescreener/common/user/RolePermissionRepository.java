package com.resumescreener.common.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermission.Key> {
    List<RolePermission> findByRole(Role role);

    // Clears the persistence context so rows re-saved in the same transaction are inserted, not merged into stale copies
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.role = :role")
    void deleteByRole(Role role);
}

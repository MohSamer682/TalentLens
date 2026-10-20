package com.resumescreener.common.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Role> {

    Optional<RoleEntity> findByNameIgnoreCase(String name);

    /** Codes stored in the table, read as text so a code the enum doesn't know still shows up. */
    @Query(value = "SELECT code || ':' || kind FROM roles", nativeQuery = true)
    List<String> storedCodesAndKinds();
}

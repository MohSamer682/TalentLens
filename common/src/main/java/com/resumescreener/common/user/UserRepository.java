package com.resumescreener.common.user;

import com.resumescreener.common.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRole(Role role);
    java.util.List<User> findByRoleIn(java.util.Collection<Role> roles);
    java.util.List<User> findByRole(Role role);
}
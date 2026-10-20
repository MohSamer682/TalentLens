package com.resumescreener.common.user;

import com.resumescreener.common.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRole(Role role);
    long countByRoleAndStatus(Role role, UserStatus status);

    /** Row-locks the matches, so two guards counting the same rows run one after the other. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.role = :role and u.status = :status")
    java.util.List<User> lockByRoleAndStatus(Role role, UserStatus status);
    java.util.List<User> findByRoleInAndStatus(java.util.Collection<Role> roles, UserStatus status);

    /** {@code pattern} is a lower-case LIKE pattern escaped with a backslash; pass "%" to match everyone. */
    @org.springframework.data.jpa.repository.Query(value = """
            select new com.resumescreener.common.user.UserRow(u.id, u.email, u.fullName, u.role, u.status, p.headline, u.createdAt)
            from User u left join UserProfile p on p.userId = u.id
            where (:status is null or u.status = :status) and (:role is null or u.role = :role)
              and (lower(u.email) like :pattern escape '\\' or lower(u.fullName) like :pattern escape '\\')
            """, countQuery = """
            select count(u) from User u
            where (:status is null or u.status = :status) and (:role is null or u.role = :role)
              and (lower(u.email) like :pattern escape '\\' or lower(u.fullName) like :pattern escape '\\')
            """)
    org.springframework.data.domain.Page<UserRow> search(@org.springframework.data.repository.query.Param("status") UserStatus status,
                                                        @org.springframework.data.repository.query.Param("role") Role role,
                                                        @org.springframework.data.repository.query.Param("pattern") String pattern,
                                                        org.springframework.data.domain.Pageable pageable);
}
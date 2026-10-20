package com.resumescreener.api.auth;

import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import com.resumescreener.common.user.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from {@code app.bootstrap.admin-email} and {@code app.bootstrap.admin-password} when no
 * active admin exists, with a password change due on first sign-in. Does nothing when either value is unset.
 */
@Slf4j
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public BootstrapAdmin(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.bootstrap.admin-email:}") String email,
                          @Value("${app.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdmin();
    }

    static final int MIN_PASSWORD = 12;

    /** Returns true when it created the admin. */
    public boolean ensureAdmin() {
        if (email.isBlank() || password.isBlank()) return false;
        if (password.length() < MIN_PASSWORD || password.toLowerCase(java.util.Locale.ROOT).contains("change-me")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD + " characters and not the example value");
        }
        if (users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) > 0) return false;
        if (users.existsByEmailIgnoreCase(email.trim())) {
            log.warn("No active admin exists, and the bootstrap admin email is already used by another account");
            return false;
        }
        User admin = User.builder().email(email.trim()).fullName("Administrator").role(Role.ADMIN)
                .passwordHash(encoder.encode(password)).build();
        admin.resetPassword(admin.getPasswordHash());
        try {
            users.saveAndFlush(admin);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Another instance starting at the same moment created it first
            log.info("The bootstrap admin was created by another instance");
            return false;
        }
        log.info("Created the bootstrap admin account");
        return true;
    }
}

package com.resumescreener.api.support;

import com.resumescreener.api.auth.JwtService;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

/** The whole API with its real security chain, over one Postgres shared by every subclass. */
@SpringBootTest(properties = {
        "jwt.secret=integration-test-secret-integration-test-secret",
        "minio.access-key=test", "minio.secret-key=test", "batch-worker.shared-secret=test"
})
@AutoConfigureMockMvc
public abstract class ApiIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    public static final String PASSWORD = "Password123!";

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected JwtService jwt;
    @Autowired protected PasswordEncoder encoder;

    protected User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@t.io").fullName("Test " + role)
                .passwordHash(encoder.encode(PASSWORD)).role(role).build());
    }

    protected String bearer(User user) {
        return "Bearer " + jwt.generateToken(user.getEmail(), user.getRole().name());
    }
}

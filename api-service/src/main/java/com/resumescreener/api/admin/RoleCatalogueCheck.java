package com.resumescreener.api.admin;

import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Refuses to start when the roles table and the {@link Role} enum disagree, since the workflow depends on both. */
@Component
@RequiredArgsConstructor
public class RoleCatalogueCheck implements ApplicationRunner {

    private final RoleRepository roles;

    @Override
    public void run(ApplicationArguments args) {
        verify(roles.storedCodesAndKinds());
    }

    static void verify(List<String> stored) {
        Set<String> expected = Arrays.stream(Role.values()).map(r -> r.name() + ":" + r.kind()).collect(Collectors.toCollection(TreeSet::new));
        Set<String> actual = new TreeSet<>(stored);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("The roles table doesn't match the Role enum. Expected " + expected + " but found " + actual);
        }
    }
}

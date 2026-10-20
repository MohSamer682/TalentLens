package com.resumescreener.api.profile;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.profile.dto.CreateUserRequest;
import com.resumescreener.api.profile.dto.ProfileView;
import com.resumescreener.api.profile.dto.UpdateUserRequest;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserProfile;
import com.resumescreener.common.user.UserProfileRepository;
import com.resumescreener.common.user.UserRepository;
import com.resumescreener.common.user.UserRow;
import com.resumescreener.common.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final ProfileService profileService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<UserRow> list(UserStatus status, Role role, String query, Pageable pageable) {
        String pattern = query == null || query.isBlank() ? "%" : "%" + query.trim().toLowerCase(Locale.ROOT).replaceAll("([\\\\%_])", "\\\\$1") + "%";
        return userRepository.search(status, role, pattern, pageable);
    }

    @Transactional(readOnly = true)
    public ProfileView get(String id) {
        User user = byId(id);
        return ProfileView.of(user, profileRepository.findById(id).map(UserProfile::fields).orElse(null));
    }

    @Transactional
    public ProfileView create(CreateUserRequest request, String actorEmail) {
        requireFreeEmail(request.email(), null);
        guardRoleKind(Role.RECRUITER, request.role());
        User user = User.builder().email(request.email().trim()).fullName(request.fullName().trim()).role(request.role())
                .passwordHash(passwordEncoder.encode(request.temporaryPassword())).mustChangePassword(true).build();
        user = userRepository.saveAndFlush(user);
        UserProfile profile = UserProfile.blankFor(user);
        profile.update(request.fields(), byEmail(actorEmail).getId(), Instant.now(clock));
        profileRepository.save(profile);
        auditService.record("USER_CREATE", "user", user.getId(), "success", "role " + user.getRole());
        return ProfileView.of(user, profile.fields());
    }

    @Transactional
    public ProfileView update(String id, UpdateUserRequest request, String actorEmail) {
        User actor = byEmail(actorEmail);
        User user = byId(id);
        ProfileService.requireVersion(user, request.version());
        Role oldRole = user.getRole();
        if (request.role() != oldRole) {
            guardRoleKind(oldRole, request.role());
            guardNotSelf(actor, user);
            if (oldRole == Role.ADMIN) guardLosingAdmin(user);
            user.setRole(request.role());
        }
        if (!user.getEmail().equalsIgnoreCase(request.email().trim())) {
            requireFreeEmail(request.email(), user.getId());
        }
        user.setEmail(request.email().trim());
        ProfileView view = profileService.update(user, request.fullName(), request.fields(), request.version(), actor.getId());
        auditService.record("USER_UPDATE", "user", id, "success",
                oldRole == request.role() ? null : "role " + oldRole + " -> " + request.role());
        if (oldRole != request.role()) recordRoleMove(user, oldRole, request.role());
        return view;
    }

    @Transactional
    public ProfileView changeRole(String id, Role role, String actorEmail) {
        User actor = byEmail(actorEmail);
        User user = byId(id);
        guardNotSelf(actor, user);
        Role oldRole = user.getRole();
        guardRoleKind(oldRole, role);
        if (oldRole == Role.ADMIN && role != Role.ADMIN) guardLosingAdmin(user);
        user.setRole(role);
        userRepository.save(user);
        auditService.record("ROLE_CHANGE", "user", id, "success", oldRole + " -> " + role);
        if (oldRole != role) recordRoleMove(user, oldRole, role);
        return get(id);
    }

    @Transactional
    public ProfileView setStatus(String id, UserStatus status, String actorEmail) {
        User user = byId(id);
        guardNotSelf(byEmail(actorEmail), user);
        if (status == user.getStatus()) return get(id);
        if (status == UserStatus.HIDDEN) {
            if (user.getRole() == Role.ADMIN) guardLosingAdmin(user);
            user.hide();
        } else {
            user.unhide();
        }
        userRepository.save(user);
        auditService.record(status == UserStatus.HIDDEN ? "USER_HIDE" : "USER_UNHIDE", "user", id, "success");
        return get(id);
    }

    @Transactional
    public ProfileView resetPassword(String id, String temporaryPassword, String actorEmail) {
        User user = byId(id);
        guardNotSelf(byEmail(actorEmail), user);
        user.resetPassword(passwordEncoder.encode(temporaryPassword));
        userRepository.saveAndFlush(user);
        auditService.record("PASSWORD_RESET", "user", id, "success");
        return get(id);
    }

    /** A hard delete: the foreign keys from sessions, interviews and notes refuse it when the user has history. */
    @Transactional
    public void delete(String id, String actorEmail) {
        User user = byId(id);
        guardNotSelf(byEmail(actorEmail), user);
        if (user.getRole() == Role.ADMIN) guardLosingAdmin(user);
        try {
            profileRepository.findById(id).ifPresent(profileRepository::delete);
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("This account has history (sessions, interviews or notes), so it can't be deleted. Hide it instead.",
                    "User " + id + " is still referenced");
        }
        auditService.record("USER_DELETE", "user", id, "success", user.getEmail());
    }

    /** A single role change also shows in both roles' history. */
    private void recordRoleMove(User user, Role from, Role to) {
        String details = from + " -> " + to + ": " + user.getEmail();
        auditService.record("ROLE_MEMBERS_MOVED", "role", to.name(), "success", details);
        auditService.record("ROLE_MEMBERS_MOVED", "role", from.name(), "success", details);
    }

    void guardNotSelf(User actor, User target) {
        if (actor.getId().equals(target.getId())) {
            throw ApiException.validation("You can't do that to your own account", "Rejected for the calling user " + actor.getId());
        }
    }

    /** Candidates come only through the portal, and portal accounts never become staff, or the other way round. */
    static void guardRoleKind(Role from, Role to) {
        if ((from == Role.CANDIDATE) != (to == Role.CANDIDATE)) {
            throw ApiException.validation("Candidate and staff accounts can't switch roles", from + " -> " + to);
        }
    }

    /** Called before an active admin loses admin rights or access. */
    void guardLosingAdmin(User target) {
        guardLosingAdmins(List.of(target));
    }

    /** The one last-admin rule: these users together may not take away every active admin. */
    void guardLosingAdmins(Collection<User> targets) {
        long leaving = targets.stream().filter(u -> u.getRole() == Role.ADMIN && u.isActive()).count();
        if (leaving > 0 && userRepository.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE).size() - leaving < 1) {
            throw ApiException.validation("At least one active admin must remain", leaving + " of the active admins would lose the role");
        }
    }

    /**
     * Moves these people into {@code role}, all or nothing, under the same rules as a single role change.
     * Returns the users that changed, with the role each one had before.
     */
    @Transactional
    public Map<User, Role> moveToRole(Role role, Collection<String> userIds, String actorEmail) {
        User actor = byEmail(actorEmail);
        // Admins first, as every single-user change does, so this and those paths take locks in the same order
        if (role != Role.ADMIN) userRepository.lockByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE);
        Set<String> wanted = new TreeSet<>(userIds);
        List<User> found = userRepository.findAllForUpdateByIdIn(wanted);
        if (found.size() != wanted.size()) {
            throw ApiException.notFound("Some of these people no longer exist. Reload and try again.", "Missing users among " + wanted);
        }
        Map<User, Role> moved = new LinkedHashMap<>();
        for (User user : found) {
            if (user.getRole() == role) continue;
            guardNotSelf(actor, user);
            guardRoleKind(user.getRole(), role);
            moved.put(user, user.getRole());
        }
        if (role != Role.ADMIN) guardLosingAdmins(moved.keySet());
        moved.keySet().forEach(u -> u.setRole(role));
        userRepository.saveAll(moved.keySet());
        moved.forEach((user, from) -> auditService.record("ROLE_CHANGE", "user", user.getId(), "success", from + " -> " + role));
        return moved;
    }

    private void requireFreeEmail(String email, String exceptId) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(u -> !u.getId().equals(exceptId))
                .ifPresent(u -> {
                    throw ApiException.conflict("An account with this email already exists", "Email is taken");
                });
    }

    private User byId(String id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found", "No user exists for id " + id));
    }

    private User byEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "No user exists for email " + email));
    }
}

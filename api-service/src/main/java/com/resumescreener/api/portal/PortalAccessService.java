package com.resumescreener.api.portal;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.auth.AuthService;
import com.resumescreener.api.auth.dto.AuthResponse;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.portal.dto.InvitePreview;
import com.resumescreener.api.portal.dto.IssuedInvite;
import com.resumescreener.api.portal.dto.PortalAccess;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.portal.CandidateInvite;
import com.resumescreener.common.portal.CandidateInviteRepository;
import com.resumescreener.common.portal.InviteToken;
import com.resumescreener.common.session.Candidate;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.ReviewStatus;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Gives a shortlisted candidate a portal sign-in, either through a one-time link or an account HR creates.
 * Every dead link (unknown, used, revoked, expired, or for a staff email) answers the same 410, so the link
 * never tells a stranger anything about accounts.
 */
@Service
@RequiredArgsConstructor
public class PortalAccessService {

    static final String GONE = "This link is no longer valid. Ask your recruiter for a new one.";

    private final CandidateInviteRepository inviteRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AuthService authService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final PortalProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PortalAccess accessOf(String sessionId, String candidateId, String email) {
        sessionService.getSession(sessionId, email);
        Candidate candidate = inSession(sessionId, candidateId);
        PortalAccess.Account account = Optional.ofNullable(candidate.getAccountUserId()).flatMap(userRepository::findById)
                .map(u -> new PortalAccess.Account(u.getEmail(), u.getCreatedAt(), u.isMustChangePassword(), u.isActive()))
                .orElse(null);
        PortalAccess.OpenInvite invite = inviteRepository.findByCandidateIdAndUsedAtIsNullAndRevokedAtIsNull(candidateId)
                .filter(i -> i.isUsable(clock.instant()))
                .map(i -> new PortalAccess.OpenInvite(i.getCreatedAt(), i.getExpiresAt()))
                .orElse(null);
        return new PortalAccess(account, invite);
    }

    /** Issuing a new link revokes the open one first; the partial unique index allows only one. */
    @Transactional
    public IssuedInvite issueInvite(String sessionId, String candidateId, String email) {
        User hr = owner(sessionId, email);
        Candidate candidate = lockEligible(sessionId, candidateId);
        if (candidate.getAccountUserId() != null) {
            throw ApiException.conflict("This candidate can already sign in", "Candidate " + candidateId + " has an account");
        }
        revokeOpen(candidateId);
        CandidateInvite.Issued issued = CandidateInvite.issue(candidate, hr, clock, properties.inviteTtl());
        inviteRepository.save(issued.invite());
        auditService.record("PORTAL_INVITE_ISSUED", "CANDIDATE", candidateId, "success");
        return new IssuedInvite(properties.inviteUrl(issued.rawToken()), issued.invite().getExpiresAt());
    }

    @Transactional
    public void revokeInvite(String sessionId, String candidateId, String email) {
        owner(sessionId, email);
        inSession(sessionId, candidateId);
        if (revokeOpen(candidateId)) {
            auditService.record("PORTAL_INVITE_REVOKED", "CANDIDATE", candidateId, "success");
        }
    }

    @Transactional
    public PortalAccess createAccount(String sessionId, String candidateId, String temporaryPassword, String email) {
        owner(sessionId, email);
        Candidate candidate = lockEligible(sessionId, candidateId);
        if (candidate.getAccountUserId() != null || userRepository.existsByEmailIgnoreCase(emailOf(candidate))) {
            throw ApiException.conflict("An account with this email already exists", "Send an access link so they can link it");
        }
        String hash = passwordEncoder.encode(temporaryPassword);
        User account = newCandidateUser(candidate, hash);
        account.resetPassword(hash);
        userRepository.save(account);
        candidate.setAccountUserId(account.getId());
        revokeOpen(candidateId);
        auditService.record("PORTAL_ACCOUNT_CREATED", "CANDIDATE", candidateId, "success");
        return accessOf(sessionId, candidateId, email);
    }

    @Transactional(readOnly = true)
    public InvitePreview preview(String rawToken) {
        CandidateInvite invite = inviteRepository.findByTokenHash(InviteToken.hash(rawToken))
                .filter(i -> i.isUsable(clock.instant()))
                .orElseThrow(PortalAccessService::gone);
        Candidate candidate = candidateRepository.findById(invite.getCandidateId()).orElseThrow(PortalAccessService::gone);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(emailOf(candidate));
        if (existing.isPresent() && existing.get().getRole() != Role.CANDIDATE) {
            throw gone();
        }
        return new InvitePreview(candidate.getSession().getJobTitle(), displayName(candidate), existing.isPresent());
    }

    /** First-time candidates: create the account with their own password and sign them in. */
    @Transactional
    public AuthResponse accept(String rawToken, String password) {
        CandidateInvite invite = lockUsable(rawToken);
        Candidate candidate = candidateRepository.findForUpdateById(invite.getCandidateId()).orElseThrow(PortalAccessService::gone);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(emailOf(candidate));
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.CANDIDATE) {
                throw gone();
            }
            throw ApiException.conflict("You already have an account. Sign in, then open this link again.",
                    "The candidate email already has a portal account");
        }
        User account = userRepository.save(newCandidateUser(candidate, passwordEncoder.encode(password)));
        redeem(invite, candidate, account);
        return authService.signIn(account);
    }

    /** Returning candidates: link this application to the account they are signed in with. */
    @Transactional
    public void claim(String rawToken, String email) {
        CandidateInvite invite = lockUsable(rawToken);
        Candidate candidate = candidateRepository.findForUpdateById(invite.getCandidateId()).orElseThrow(PortalAccessService::gone);
        User account = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getRole() == Role.CANDIDATE && u.getEmail().equalsIgnoreCase(emailOf(candidate)))
                .orElseThrow(PortalAccessService::gone);
        redeem(invite, candidate, account);
    }

    private void redeem(CandidateInvite invite, Candidate candidate, User account) {
        candidate.setAccountUserId(account.getId());
        invite.markUsed(clock.instant());
        candidateRepository.saveAndFlush(candidate);
        auditService.record("PORTAL_INVITE_REDEEMED", "CANDIDATE", candidate.getId(), "success");
    }

    private CandidateInvite lockUsable(String rawToken) {
        return inviteRepository.lockByTokenHash(InviteToken.hash(rawToken))
                .filter(i -> i.isUsable(clock.instant()))
                .orElseThrow(PortalAccessService::gone);
    }

    private boolean revokeOpen(String candidateId) {
        return inviteRepository.findByCandidateIdAndUsedAtIsNullAndRevokedAtIsNull(candidateId).map(open -> {
            open.revoke(clock.instant());
            inviteRepository.saveAndFlush(open);
            return true;
        }).orElse(false);
    }

    private User owner(String sessionId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("User not found", "No user exists for email " + email));
    }

    private Candidate inSession(String sessionId, String candidateId) {
        return candidateRepository.findByIdAndSessionId(candidateId, sessionId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate " + candidateId + " in session " + sessionId));
    }

    private Candidate lockEligible(String sessionId, String candidateId) {
        inSession(sessionId, candidateId);
        Candidate candidate = candidateRepository.findForUpdateById(candidateId)
                .orElseThrow(() -> ApiException.notFound("Candidate not found", "No candidate with id " + candidateId));
        if (candidate.getReviewStatus() == ReviewStatus.NONE) {
            throw ApiException.validation("Shortlist the candidate before giving them portal access", "Review status is NONE");
        }
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) {
            throw ApiException.validation("Add the candidate's email first", "The candidate has no email");
        }
        return candidate;
    }

    private static User newCandidateUser(Candidate candidate, String passwordHash) {
        return User.builder()
                .email(emailOf(candidate))
                .fullName(displayName(candidate))
                .passwordHash(passwordHash)
                .role(Role.CANDIDATE)
                .build();
    }

    private static String emailOf(Candidate candidate) {
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) {
            throw gone();
        }
        return candidate.getEmail().trim();
    }

    private static String displayName(Candidate candidate) {
        if (candidate.getName() != null && !candidate.getName().isBlank()) {
            return candidate.getName().trim();
        }
        String email = emailOf(candidate);
        return email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
    }

    private static ApiException gone() {
        return ApiException.gone(GONE, "The access link is unknown, used, revoked or expired");
    }
}

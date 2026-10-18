package com.resumescreener.api.pipeline;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.SubmitFeedbackRequest;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PipelineServiceTest {

    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CandidateInterviewRepository interviews = mock(CandidateInterviewRepository.class);
    private final CandidateOfferRepository offers = mock(CandidateOfferRepository.class);
    private final CandidateStageEventRepository events = mock(CandidateStageEventRepository.class);
    private final SessionService sessions = mock(SessionService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final PipelineService service = new PipelineService(candidates, mock(CandidateScoreRepository.class),
            interviews, offers, events, sessions, mock(ScreeningSessionRepository.class), users, audit,
            mock(MinioUploadService.class));

    private final User recruiter = user("u1", "r@example.com", Role.RECRUITER);
    private final User chief = user("u2", "chief@example.com", Role.HR_CHIEF);
    private final User tech = user("u3", "tech@example.com", Role.TECHNICAL_INTERVIEWER);
    private final User hr = user("u4", "hr@example.com", Role.HR_INTERVIEWER);
    private final Candidate candidate = Candidate.builder().id("c1").build();
    private final List<CandidateInterview> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        for (User u : List.of(recruiter, chief, tech, hr)) {
            when(users.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
        }
        when(candidates.findByIdAndSessionId("c1", "s1")).thenReturn(Optional.of(candidate));
        when(candidates.findForUpdateById("c1")).thenReturn(Optional.of(candidate));
        when(interviews.findByCandidateIdOrderByScheduledStartAsc("c1")).thenReturn(stored);
        when(interviews.saveAndFlush(any())).thenAnswer(i -> {
            CandidateInterview iv = i.getArgument(0);
            if (iv.getId() == null) iv.setId("i" + stored.size());
            if (!stored.contains(iv)) stored.add(iv);
            return iv;
        });
    }

    @Test
    void contactsOnlyFromShortlisted() {
        ApiException e = assertThrows(ApiException.class, () -> service.markContacted("s1", "c1", recruiter.getEmail()));
        assertEquals("This candidate is at Screened, so this step isn't available", e.getMessage());

        candidate.setReviewStatus(ReviewStatus.SHORTLISTED);
        service.markContacted("s1", "c1", recruiter.getEmail());
        assertEquals(ReviewStatus.CONTACTED, candidate.getReviewStatus());
        verify(events).save(any());
    }

    @Test
    void viewerWithoutOwnershipCannotContact() {
        candidate.setReviewStatus(ReviewStatus.SHORTLISTED);
        when(sessions.getOwnedSession("s1", chief.getEmail())).thenThrow(new AccessDeniedException("no"));

        assertThrows(AccessDeniedException.class, () -> service.markContacted("s1", "c1", chief.getEmail()));
        assertEquals(ReviewStatus.SHORTLISTED, candidate.getReviewStatus());
    }

    @Test
    void candidateFromAnotherSessionIs404() {
        assertThrows(ApiException.class, () -> service.getTimeline("s1", "c-elsewhere", recruiter.getEmail()));
        assertThrows(ApiException.class, () -> service.getInterviews("s1", "c-elsewhere", recruiter.getEmail()));
    }

    @Test
    void refusesWrongInterviewerKind() {
        candidate.setReviewStatus(ReviewStatus.CONTACTED);
        ApiException e = assertThrows(ApiException.class, () -> service.scheduleInterview("s1", "c1",
                schedule(InterviewKind.TECHNICAL, hr), recruiter.getEmail()));
        assertEquals("Choose a technical interviewer for a technical interview", e.getMessage());
    }

    @Test
    void stageFollowsTheInterviews() {
        candidate.setReviewStatus(ReviewStatus.CONTACTED);
        service.scheduleInterview("s1", "c1", schedule(InterviewKind.TECHNICAL, tech), recruiter.getEmail());
        service.scheduleInterview("s1", "c1", schedule(InterviewKind.HR, hr), recruiter.getEmail());
        assertEquals(ReviewStatus.INTERVIEW_SCHEDULED, candidate.getReviewStatus());

        CandidateInterview technical = stored.get(0);
        CandidateInterview hrInterview = stored.get(1);
        when(interviews.findById(technical.getId())).thenReturn(Optional.of(technical));
        when(interviews.findById(hrInterview.getId())).thenReturn(Optional.of(hrInterview));

        service.submitFeedback(technical.getId(), feedback(), tech.getEmail());
        assertEquals(ReviewStatus.INTERVIEW_SCHEDULED, candidate.getReviewStatus());
        service.submitFeedback(hrInterview.getId(), feedback(), hr.getEmail());
        assertEquals(ReviewStatus.INTERVIEWED, candidate.getReviewStatus());
    }

    @Test
    void cancellingTheOnlyInterviewReturnsToContacted() {
        candidate.setReviewStatus(ReviewStatus.CONTACTED);
        service.scheduleInterview("s1", "c1", schedule(InterviewKind.TECHNICAL, tech), recruiter.getEmail());
        CandidateInterview only = stored.get(0);
        when(interviews.findById(only.getId())).thenReturn(Optional.of(only));

        service.cancelInterview("s1", "c1", only.getId(), recruiter.getEmail());

        assertEquals(ReviewStatus.CONTACTED, candidate.getReviewStatus());
    }

    @Test
    void onlyTheAssignedInterviewerGivesFeedbackAfterTheStart() {
        CandidateInterview later = CandidateInterview.builder().id("i9").candidateId("c1").interviewer(tech)
                .kind(InterviewKind.TECHNICAL).scheduledStart(Instant.now().plusSeconds(3600)).build();
        when(interviews.findById("i9")).thenReturn(Optional.of(later));

        assertThrows(ApiException.class, () -> service.submitFeedback("i9", feedback(), hr.getEmail()));
        ApiException early = assertThrows(ApiException.class, () -> service.submitFeedback("i9", feedback(), tech.getEmail()));
        assertEquals("You can give feedback once the interview has started", early.getMessage());
    }

    @Test
    void uploaderCannotApprove() {
        candidate.setReviewStatus(ReviewStatus.OFFER);
        when(offers.findByCandidateId("c1")).thenReturn(Optional.of(
                CandidateOffer.builder().id("o1").candidateId("c1").uploadedBy(chief).build()));

        ApiException e = assertThrows(ApiException.class, () -> service.approveOffer("s1", "c1", chief.getEmail()));
        assertEquals("Someone other than the uploader must approve this offer", e.getMessage());
        assertEquals(ReviewStatus.OFFER, candidate.getReviewStatus());
        verify(offers, never()).save(any());
    }

    private static com.resumescreener.api.candidate.dto.ScheduleInterviewRequest schedule(InterviewKind kind, User interviewer) {
        return new com.resumescreener.api.candidate.dto.ScheduleInterviewRequest(kind, interviewer.getId(),
                Instant.now().minusSeconds(60), 60, null, null);
    }

    private static SubmitFeedbackRequest feedback() {
        return new SubmitFeedbackRequest(InterviewRecommendation.HIRE, 4, "Solid answers on system design.");
    }

    private static User user(String id, String email, Role role) {
        return User.builder().id(id).email(email).fullName(id).role(role).build();
    }
}

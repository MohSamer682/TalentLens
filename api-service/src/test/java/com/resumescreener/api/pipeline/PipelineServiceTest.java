package com.resumescreener.api.pipeline;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.OfferAnswerRequest;
import com.resumescreener.api.candidate.dto.PlanInterviewsRequest;
import com.resumescreener.api.candidate.dto.RejectOfferRequest;
import com.resumescreener.api.candidate.dto.ScheduleInterviewRequest;
import com.resumescreener.api.candidate.dto.SubmitFeedbackRequest;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import com.resumescreener.common.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PipelineServiceTest {

    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CandidateInterviewRepository interviews = mock(CandidateInterviewRepository.class);
    private final CandidateOfferRepository offers = mock(CandidateOfferRepository.class);
    private final CandidateStageEventRepository events = mock(CandidateStageEventRepository.class);
    private final ScreeningSessionRepository sessions = mock(ScreeningSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final AuditService audit = mock(AuditService.class);
    private final PipelineSupport support = new PipelineSupport(candidates, events, sessions, users, sessionService, audit);
    private final PipelineService service = new PipelineService(candidates, interviews, events, users, sessionService, audit, support);
    private final OfferService offerService = new OfferService(candidates, offers, sessionService, audit, mock(MinioUploadService.class), support);

    private final User recruiter = user("r1", "recruiter@x.com", Role.RECRUITER);
    private final User director = user("d1", "director@x.com", Role.DEPARTMENT_DIRECTOR);
    private final User hrChief = user("h1", "chief@x.com", Role.HR_CHIEF);

    @BeforeEach
    void approverAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "x", "OFFER_APPROVE"));
        when(sessions.existsById("s1")).thenReturn(true);
        for (User u : List.of(recruiter, director, hrChief)) {
            when(users.findByEmailIgnoreCase(u.getEmail())).thenReturn(Optional.of(u));
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static User user(String id, String email, Role role) {
        return User.builder().id(id).email(email).fullName(role.name()).role(role).build();
    }

    private CandidateOffer offer(boolean needsDirector) {
        CandidateOffer o = CandidateOffer.builder().id("o1").candidateId("c1").fileKey("k").fileName("offer.pdf")
                .uploadedBy(recruiter).requiresDirectorApproval(needsDirector).build();
        when(offers.findByCandidateId("c1")).thenReturn(Optional.of(o));
        when(offers.save(any())).thenAnswer(i -> i.getArgument(0));
        return o;
    }

    private Candidate candidate(ReviewStatus stage, String plan) {
        Candidate c = Candidate.builder().id("c1").reviewStatus(stage).interviewPlan(plan).build();
        when(candidates.findByIdAndSessionId("c1", "s1")).thenReturn(Optional.of(c));
        when(candidates.findById("c1")).thenReturn(Optional.of(c));
        when(candidates.findForUpdateById("c1")).thenReturn(Optional.of(c));
        return c;
    }

    private static CandidateInterview held(InterviewKind kind) {
        return CandidateInterview.builder().candidateId("c1").kind(kind).status(InterviewStatus.COMPLETED).build();
    }

    // --- interview place ---

    @Test
    void hqNeedsNoDetail() {
        assertNull(PipelineService.resolveLocation(InterviewPlace.HQ, "ignored"));
    }

    @Test
    void onlineKeepsTheLinkAndRejectsTextThatIsNotAWebLink() {
        assertEquals("https://meet.example.com/a", PipelineService.resolveLocation(InterviewPlace.ONLINE, "  https://meet.example.com/a "));
        assertThrows(ApiException.class, () -> PipelineService.resolveLocation(InterviewPlace.ONLINE, "meeting room 3"));
        assertThrows(ApiException.class, () -> PipelineService.resolveLocation(InterviewPlace.ONLINE, "javascript:alert(1)"));
        assertThrows(ApiException.class, () -> PipelineService.resolveLocation(InterviewPlace.ONLINE, null));
    }

    @Test
    void otherNeedsText() {
        assertEquals("Cedar room", PipelineService.resolveLocation(InterviewPlace.OTHER, " Cedar room "));
        assertThrows(ApiException.class, () -> PipelineService.resolveLocation(InterviewPlace.OTHER, "  "));
    }

    // --- interview plan ---

    @Test
    void managementInterviewsGoToTopManagementOnly() {
        candidate(ReviewStatus.CONTACTED, "MANAGEMENT");
        when(users.findById("t1")).thenReturn(Optional.of(user("t1", "t@x.com", Role.TECHNICAL_INTERVIEWER)));
        when(users.findById("d1")).thenReturn(Optional.of(director));
        when(interviews.save(any())).thenAnswer(i -> i.getArgument(0));

        var wrong = new ScheduleInterviewRequest(InterviewKind.MANAGEMENT, "t1", Instant.now(), 60, InterviewPlace.HQ, null, null);
        assertThrows(ApiException.class, () -> service.scheduleInterview("s1", "c1", wrong, "recruiter@x.com"));

        var right = new ScheduleInterviewRequest(InterviewKind.MANAGEMENT, "d1", Instant.now(), 60, InterviewPlace.ONLINE, "https://meet.example.com/m", null);
        assertEquals("https://meet.example.com/m", service.scheduleInterview("s1", "c1", right, "recruiter@x.com").location());
    }

    @Test
    void cannotScheduleAKindOutsideThePlan() {
        candidate(ReviewStatus.CONTACTED, "HR");
        when(users.findById("t1")).thenReturn(Optional.of(user("t1", "t@x.com", Role.TECHNICAL_INTERVIEWER)));
        var req = new ScheduleInterviewRequest(InterviewKind.TECHNICAL, "t1", Instant.now(), 60, InterviewPlace.HQ, null, null);
        assertThrows(ApiException.class, () -> service.scheduleInterview("s1", "c1", req, "recruiter@x.com"));
    }

    @Test
    void candidateIsInterviewedOnceEveryPlannedKindIsHeld() {
        Candidate c = candidate(ReviewStatus.INTERVIEW_SCHEDULED, "HR,TECHNICAL");
        when(interviews.findByCandidateIdOrderByScheduledStartAsc("c1")).thenReturn(List.of(held(InterviewKind.HR)));

        service.setInterviewPlan("s1", "c1", new PlanInterviewsRequest(List.of(InterviewKind.HR, InterviewKind.TECHNICAL)), "recruiter@x.com");
        assertEquals(ReviewStatus.INTERVIEW_SCHEDULED, c.getReviewStatus(), "technical is still to come");

        service.setInterviewPlan("s1", "c1", new PlanInterviewsRequest(List.of(InterviewKind.HR)), "recruiter@x.com");
        assertEquals(ReviewStatus.INTERVIEWED, c.getReviewStatus());
    }

    @Test
    void cannotDropAKindThatIsAlreadyScheduled() {
        candidate(ReviewStatus.INTERVIEW_SCHEDULED, "HR,TECHNICAL");
        var scheduled = CandidateInterview.builder().candidateId("c1").kind(InterviewKind.TECHNICAL).status(InterviewStatus.SCHEDULED).build();
        when(interviews.findByCandidateIdOrderByScheduledStartAsc("c1")).thenReturn(List.of(scheduled));
        assertThrows(ApiException.class, () ->
                service.setInterviewPlan("s1", "c1", new PlanInterviewsRequest(List.of(InterviewKind.HR)), "recruiter@x.com"));
    }

    // --- offer approval chain ---

    @Test
    void directorSignsFirstThenOnlyTheHrChiefFinishesIt() {
        CandidateOffer o = offer(true);
        candidate(ReviewStatus.OFFER, null);

        assertThrows(ApiException.class, () -> offerService.approveOffer("s1", "c1", "chief@x.com"), "HR chief cannot skip the director");
        offerService.approveOffer("s1", "c1", "director@x.com");
        assertNotNull(o.getDirectorApprovedAt());
        assertNull(o.getApprovedAt());

        assertThrows(ApiException.class, () -> offerService.approveOffer("s1", "c1", "director@x.com"), "director cannot give the final approval");
        offerService.approveOffer("s1", "c1", "chief@x.com");
        assertNotNull(o.getApprovedAt());
    }

    @Test
    void withoutTheDirectorStepTheHrChiefApprovesAlone() {
        CandidateOffer o = offer(false);
        Candidate c = candidate(ReviewStatus.OFFER, null);
        offerService.approveOffer("s1", "c1", "chief@x.com");
        assertNotNull(o.getApprovedAt());
        assertEquals(ReviewStatus.OFFER_APPROVED, c.getReviewStatus());
    }

    @Test
    void rejectionNoteIsOptionalAndBlocksApprovalUntilRevised() {
        CandidateOffer o = offer(false);
        candidate(ReviewStatus.OFFER, null);
        offerService.rejectOffer("s1", "c1", new RejectOfferRequest("   "), "chief@x.com");
        assertNotNull(o.getRejectedAt());
        assertNull(o.getRejectionNote());
        assertThrows(ApiException.class, () -> offerService.approveOffer("s1", "c1", "chief@x.com"));
    }

    @Test
    void rejectionKeepsTheNote() {
        CandidateOffer o = offer(false);
        candidate(ReviewStatus.OFFER, null);
        offerService.rejectOffer("s1", "c1", new RejectOfferRequest(" Salary above band "), "chief@x.com");
        assertEquals("Salary above band", o.getRejectionNote());
    }

    // --- after the offer ---

    @Test
    void offerSentThenAcceptedThenJoiningDate() {
        CandidateOffer o = offer(false);
        Candidate c = candidate(ReviewStatus.OFFER_APPROVED, null);
        offerService.markOfferSent("s1", "c1", "recruiter@x.com");
        assertEquals(ReviewStatus.OFFER_SENT, c.getReviewStatus());
        offerService.answerOffer("s1", "c1", new OfferAnswerRequest(true), "recruiter@x.com");
        assertEquals(ReviewStatus.ACCEPTED, c.getReviewStatus());
        assertEquals("ACCEPTED", o.getCandidateResponse());
    }

    @Test
    void cannotRecordAnAnswerBeforeTheOfferIsSent() {
        offer(false);
        candidate(ReviewStatus.OFFER_APPROVED, null);
        assertThrows(ApiException.class, () -> offerService.answerOffer("s1", "c1", new OfferAnswerRequest(true), "recruiter@x.com"));
    }

    // --- access ---

    @Test
    void viewerWithoutOwnershipCannotContact() {
        Candidate c = candidate(ReviewStatus.SHORTLISTED, null);
        when(sessionService.getOwnedSession("s1", "chief@x.com")).thenThrow(new AccessDeniedException("no"));

        assertThrows(AccessDeniedException.class, () -> service.markContacted("s1", "c1", "chief@x.com"));
        assertEquals(ReviewStatus.SHORTLISTED, c.getReviewStatus());
    }

    @Test
    void candidateFromAnotherSessionIs404() {
        assertThrows(ApiException.class, () -> service.getTimeline("s1", "c-elsewhere", "recruiter@x.com"));
        assertThrows(ApiException.class, () -> service.getInterviews("s1", "c-elsewhere", "recruiter@x.com"));
        assertThrows(ApiException.class, () -> offerService.getOffer("s1", "c-elsewhere", "chief@x.com"));
    }

    @Test
    void onlyTheAssignedInterviewerGivesFeedbackAfterTheStart() {
        User admin = user("a1", "admin@x.com", Role.ADMIN);
        when(users.findByEmailIgnoreCase(admin.getEmail())).thenReturn(Optional.of(admin));
        CandidateInterview later = CandidateInterview.builder().id("i9").candidateId("c1").interviewer(director)
                .kind(InterviewKind.MANAGEMENT).status(InterviewStatus.SCHEDULED)
                .scheduledStart(Instant.now().plusSeconds(3600)).build();
        when(interviews.findById("i9")).thenReturn(Optional.of(later));

        assertThrows(ApiException.class, () -> service.submitFeedback("i9", feedback(), "recruiter@x.com"));
        assertThrows(ApiException.class, () -> service.submitFeedback("i9", feedback(), admin.getEmail()));
        ApiException early = assertThrows(ApiException.class, () -> service.submitFeedback("i9", feedback(), "director@x.com"));
        assertEquals("You can give feedback once the interview has started", early.getMessage());
    }

    @Test
    void cancelledInterviewTakesNoFeedback() {
        CandidateInterview cancelled = CandidateInterview.builder().id("i8").candidateId("c1").interviewer(director)
                .kind(InterviewKind.MANAGEMENT).status(InterviewStatus.CANCELLED)
                .scheduledStart(Instant.now().minusSeconds(3600)).build();
        when(interviews.findById("i8")).thenReturn(Optional.of(cancelled));

        ApiException e = assertThrows(ApiException.class, () -> service.submitFeedback("i8", feedback(), "director@x.com"));
        assertEquals("This interview was cancelled", e.getMessage());
    }

    private static SubmitFeedbackRequest feedback() {
        return new SubmitFeedbackRequest(InterviewRecommendation.HIRE, 4, "Solid answers on system design.");
    }
}

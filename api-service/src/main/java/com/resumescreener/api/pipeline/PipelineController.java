package com.resumescreener.api.pipeline;

import com.resumescreener.api.candidate.dto.*;
import com.resumescreener.api.common.ApiResponse;
import com.resumescreener.common.session.InterviewKind;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final OfferService offerService;
    private final PipelineQueryService queryService;

    @GetMapping("/api/sessions/{sessionId}/candidates/{candidateId}/timeline")
    public ResponseEntity<ApiResponse<List<CandidateStageEventDto>>> getTimeline(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Timeline loaded",
                pipelineService.getTimeline(sessionId, candidateId, auth.getName())));
    }

    @GetMapping("/api/sessions/{sessionId}/candidates/{candidateId}/interviews")
    public ResponseEntity<ApiResponse<List<CandidateInterviewDto>>> getInterviews(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interviews loaded",
                pipelineService.getInterviews(sessionId, candidateId, auth.getName())));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/contact")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> markContacted(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        pipelineService.markContacted(sessionId, candidateId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Candidate marked as contacted", null));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/interviews")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateInterviewDto>> scheduleInterview(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @Valid @RequestBody ScheduleInterviewRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interview scheduled",
                pipelineService.scheduleInterview(sessionId, candidateId, req, auth.getName())));
    }

    @PatchMapping("/api/sessions/{sessionId}/candidates/{candidateId}/interviews/{interviewId}")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateInterviewDto>> rescheduleInterview(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @PathVariable String interviewId,
            @Valid @RequestBody RescheduleInterviewRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interview rescheduled",
                pipelineService.rescheduleInterview(sessionId, candidateId, interviewId, req, auth.getName())));
    }

    @DeleteMapping("/api/sessions/{sessionId}/candidates/{candidateId}/interviews/{interviewId}")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> cancelInterview(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @PathVariable String interviewId,
            Authentication auth) {
        pipelineService.cancelInterview(sessionId, candidateId, interviewId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Interview cancelled", null));
    }

    @PostMapping("/api/interviews/{interviewId}/feedback")
    @PreAuthorize("hasAuthority('INTERVIEW_FEEDBACK')")
    public ResponseEntity<ApiResponse<CandidateInterviewDto>> submitFeedback(
            @PathVariable String interviewId,
            @Valid @RequestBody SubmitFeedbackRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Feedback submitted",
                pipelineService.submitFeedback(interviewId, req, auth.getName())));
    }

    @GetMapping("/api/my-interviews")
    @PreAuthorize("hasAuthority('INTERVIEW_FEEDBACK')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyInterviews(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interviews loaded",
                queryService.getMyInterviews(auth.getName())));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/move-to-offer")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> moveToOffer(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        offerService.moveToOffer(sessionId, candidateId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Moved to offer stage", null));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/offer")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateOfferDto>> uploadOffer(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "requiresDirectorApproval", defaultValue = "false") boolean requiresDirectorApproval,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Offer uploaded",
                offerService.uploadOffer(sessionId, candidateId, file, requiresDirectorApproval, auth.getName())));
    }

    @GetMapping("/api/sessions/{sessionId}/candidates/{candidateId}/interview-plan")
    public ResponseEntity<ApiResponse<List<InterviewKind>>> getInterviewPlan(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interview plan loaded",
                pipelineService.getInterviewPlan(sessionId, candidateId, auth.getName())));
    }

    @PutMapping("/api/sessions/{sessionId}/candidates/{candidateId}/interview-plan")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<List<InterviewKind>>> setInterviewPlan(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @Valid @RequestBody PlanInterviewsRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interview plan saved",
                pipelineService.setInterviewPlan(sessionId, candidateId, req, auth.getName())));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/offer/reject")
    @PreAuthorize("hasAuthority('OFFER_APPROVE')")
    public ResponseEntity<ApiResponse<CandidateOfferDto>> rejectOffer(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @Valid @RequestBody(required = false) RejectOfferRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Offer rejected",
                offerService.rejectOffer(sessionId, candidateId, req, auth.getName())));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/offer/sent")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateOfferDto>> markOfferSent(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Offer marked as sent",
                offerService.markOfferSent(sessionId, candidateId, auth.getName())));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/offer/answer")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateOfferDto>> answerOffer(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @Valid @RequestBody OfferAnswerRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Candidate's answer recorded",
                offerService.answerOffer(sessionId, candidateId, req, auth.getName())));
    }

    @PutMapping("/api/sessions/{sessionId}/candidates/{candidateId}/joining")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<CandidateOfferDto>> saveJoining(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @Valid @RequestBody JoiningRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Joining details saved",
                offerService.saveJoining(sessionId, candidateId, req, auth.getName())));
    }

    @GetMapping("/api/sessions/{sessionId}/candidates/{candidateId}/offer")
    public ResponseEntity<ApiResponse<CandidateOfferDto>> getOffer(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Offer loaded",
                offerService.getOffer(sessionId, candidateId, auth.getName()).orElse(null)));
    }

    @GetMapping("/api/sessions/{sessionId}/candidates/{candidateId}/offer/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> downloadOffer(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        String url = offerService.getOfferDownloadUrl(sessionId, candidateId, auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Download URL generated", Map.of("url", url)));
    }

    @PostMapping("/api/sessions/{sessionId}/candidates/{candidateId}/offer/approve")
    @PreAuthorize("hasAuthority('OFFER_APPROVE')")
    public ResponseEntity<ApiResponse<CandidateOfferDto>> approveOffer(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Offer approved",
                offerService.approveOffer(sessionId, candidateId, auth.getName())));
    }

    @GetMapping("/api/pipeline")
    @PreAuthorize("hasAnyAuthority('PIPELINE_MANAGE', 'OFFER_APPROVE')")
    public ResponseEntity<ApiResponse<List<PipelineCandidateDto>>> getPipeline(
            @RequestParam(required = false) Boolean awaitingApprovalOnly,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Pipeline loaded",
                queryService.getPipeline(awaitingApprovalOnly, auth.getName())));
    }

    @GetMapping("/api/interviewers")
    @PreAuthorize("hasAuthority('PIPELINE_MANAGE')")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getInterviewers(
            @RequestParam(required = false) InterviewKind kind) {
        return ResponseEntity.ok(ApiResponse.success("Interviewers loaded",
                queryService.getInterviewers(kind)));
    }

    @GetMapping("/api/interviews/{interviewId}")
    @PreAuthorize("hasAnyAuthority('INTERVIEW_FEEDBACK', 'PIPELINE_MANAGE', 'OFFER_APPROVE')")
    public ResponseEntity<ApiResponse<InterviewDetailDto>> getInterview(
            @PathVariable String interviewId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Interview loaded",
                queryService.getInterviewDetail(interviewId, auth.getName())));
    }
}

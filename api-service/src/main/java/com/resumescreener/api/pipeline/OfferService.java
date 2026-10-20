package com.resumescreener.api.pipeline;

import com.resumescreener.api.audit.AuditService;
import com.resumescreener.api.candidate.dto.*;
import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.api.upload.MinioUploadService;
import com.resumescreener.common.session.*;
import com.resumescreener.common.user.Role;
import com.resumescreener.common.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/** The offer half of the pipeline: upload, the approval chain, sending, the candidate's answer and joining. */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final CandidateRepository candidateRepository;
    private final CandidateOfferRepository offerRepository;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final MinioUploadService uploadService;
    private final PipelineSupport support;


    @Transactional(readOnly = true)
    public Optional<CandidateOfferDto> getOffer(String sessionId, String candidateId, String email) {
        support.sessionForOffer(sessionId, email);
        support.candidateInSession(sessionId, candidateId);
        return offerRepository.findByCandidateId(candidateId).map(CandidateOfferDto::from);
    }

    @Transactional
    public void moveToOffer(String sessionId, String candidateId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);

        if (candidate.getReviewStatus() != ReviewStatus.INTERVIEWED) {
            throw ApiException.validation("Cannot move to offer", "Candidate must be at INTERVIEWED stage");
        }

        User actor = support.getUser(email);
        candidate.setReviewStatus(ReviewStatus.OFFER);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        support.recordEvent(candidateId, "OFFER", actor, "Moved to offer by " + actor.getFullName());
        auditService.record("CANDIDATE_MOVE_TO_OFFER", "CANDIDATE", candidateId, "SUCCESS");
    }

    @Transactional
    public CandidateOfferDto uploadOffer(String sessionId, String candidateId, MultipartFile file, boolean requiresDirectorApproval, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);

        if (candidate.getReviewStatus() != ReviewStatus.OFFER) {
            throw ApiException.validation("Cannot upload offer", "Candidate must be at OFFER stage");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("Could not read file", e.getMessage());
        }

        if (bytes.length > 10L * 1024 * 1024) {
            throw ApiException.validation("Upload the offer as a PDF up to 10 MB", "File is " + bytes.length + " bytes");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw ApiException.validation("Upload the offer as a PDF up to 10 MB", "File does not start with %PDF-");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "offer.pdf";
        String fileKey = uploadService.uploadOfferFile(sessionId, candidateId, fileName, bytes);

        User uploader = support.getUser(email);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId).orElse(null);
        if (offer == null) {
            offer = CandidateOffer.builder().candidateId(candidateId).build();
        }
        // A new or revised offer restarts the approval chain
        offer.setFileKey(fileKey);
        offer.setFileName(fileName);
        offer.setSizeBytes(bytes.length);
        offer.setUploadedBy(uploader);
        offer.setUploadedAt(Instant.now());
        offer.setRequiresDirectorApproval(requiresDirectorApproval);
        offer.setApprovedBy(null);
        offer.setApprovedAt(null);
        offer.setDirectorApprovedBy(null);
        offer.setDirectorApprovedAt(null);
        offer.setRejectedBy(null);
        offer.setRejectedAt(null);
        offer.setRejectionNote(null);
        offer = offerRepository.save(offer);

        support.recordEvent(candidateId, "OFFER", uploader, "Offer uploaded by " + uploader.getFullName()
                + (requiresDirectorApproval ? " (department director approval required)" : ""));
        auditService.record("OFFER_UPLOAD", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    /**
     * One call, two possible steps. While the department director's sign-off is outstanding only a
     * department director (or admin) can give it; after that, only the HR chief (or admin) gives the final one.
     */
    @Transactional
    public CandidateOfferDto approveOffer(String sessionId, String candidateId, String email) {
        support.sessionForOffer(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));

        User approver = support.getUser(email);
        if (offer.getUploadedBy() != null && offer.getUploadedBy().getId().equals(approver.getId())) {
            throw ApiException.validation("Cannot approve your own offer", "A different approver is required to approve this offer");
        }
        if (offer.getApprovedAt() != null) {
            throw ApiException.validation("Offer already approved", "This offer has already been approved");
        }
        if (offer.getRejectedAt() != null) {
            throw ApiException.validation("Offer was rejected", "The recruiter has to upload a revised offer first");
        }

        if (PipelineSupport.directorStepPending(offer)) {
            PipelineSupport.requireRole(approver, "Waiting for the department director", Role.DEPARTMENT_DIRECTOR);
            offer.setDirectorApprovedBy(approver);
            offer.setDirectorApprovedAt(Instant.now());
            offer = offerRepository.save(offer);
            support.recordEvent(candidateId, "OFFER_DIRECTOR_APPROVED", approver, "Offer approved by department director " + approver.getFullName());
            auditService.record("OFFER_DIRECTOR_APPROVE", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");
            return CandidateOfferDto.from(offer);
        }

        PipelineSupport.requireRole(approver, "Only the HR chief can give the final approval", Role.HR_CHIEF);
        offer.setApprovedBy(approver);
        offer.setApprovedAt(Instant.now());
        offer = offerRepository.save(offer);

        candidate.setReviewStatus(ReviewStatus.OFFER_APPROVED);
        candidate.setReviewedAt(Instant.now());
        candidateRepository.save(candidate);

        support.recordEvent(candidateId, "OFFER_APPROVED", approver, "Offer approved by " + approver.getFullName());
        auditService.record("OFFER_APPROVE", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    /** Whoever holds the current approval step may send the offer back; the note is optional. */
    @Transactional
    public CandidateOfferDto rejectOffer(String sessionId, String candidateId, RejectOfferRequest req, String email) {
        support.sessionForOffer(sessionId, email);
        support.lockCandidate(sessionId, candidateId);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));
        if (offer.getApprovedAt() != null) {
            throw ApiException.validation("Offer already approved", "An approved offer cannot be rejected");
        }
        if (offer.getRejectedAt() != null) {
            throw ApiException.validation("Offer already rejected", "Waiting for the recruiter to upload a revised offer");
        }

        User rejecter = support.getUser(email);
        if (PipelineSupport.directorStepPending(offer)) {
            PipelineSupport.requireRole(rejecter, "Waiting for the department director", Role.DEPARTMENT_DIRECTOR);
        } else {
            PipelineSupport.requireRole(rejecter, "Only the HR chief can reject at this step", Role.HR_CHIEF);
        }

        String note = req == null || req.note() == null || req.note().isBlank() ? null : req.note().trim();
        offer.setRejectedBy(rejecter);
        offer.setRejectedAt(Instant.now());
        offer.setRejectionNote(note);
        offer = offerRepository.save(offer);

        support.recordEvent(candidateId, "OFFER_REJECTED", rejecter,
                "Offer rejected by " + rejecter.getFullName() + (note != null ? ": " + note : ""));
        auditService.record("OFFER_REJECT", "CANDIDATE_OFFER", offer.getId(), "SUCCESS");

        return CandidateOfferDto.from(offer);
    }

    @Transactional
    public CandidateOfferDto markOfferSent(String sessionId, String candidateId, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.candidateInStage(sessionId, candidateId, ReviewStatus.OFFER_APPROVED, "Offer must be approved before it is sent");
        CandidateOffer offer = offerOf(candidateId);

        offer.setSentAt(Instant.now());
        offerRepository.save(offer);
        User actor = support.getUser(email);
        support.moveTo(candidate, ReviewStatus.OFFER_SENT, "OFFER_SENT", actor, "Offer sent to the candidate by " + actor.getFullName());
        return CandidateOfferDto.from(offer);
    }

    @Transactional
    public CandidateOfferDto answerOffer(String sessionId, String candidateId, OfferAnswerRequest req, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.candidateInStage(sessionId, candidateId, ReviewStatus.OFFER_SENT, "The offer must be sent before the candidate can answer");
        CandidateOffer offer = offerOf(candidateId);

        boolean accepted = Boolean.TRUE.equals(req.accepted());
        offer.setCandidateResponse(accepted ? "ACCEPTED" : "DECLINED");
        offer.setRespondedAt(Instant.now());
        offerRepository.save(offer);
        User actor = support.getUser(email);
        support.moveTo(candidate, accepted ? ReviewStatus.ACCEPTED : ReviewStatus.DECLINED, accepted ? "ACCEPTED" : "DECLINED", actor,
                "Candidate " + (accepted ? "accepted" : "declined") + " the offer, recorded by " + actor.getFullName());
        return CandidateOfferDto.from(offer);
    }

    @Transactional
    public CandidateOfferDto saveJoining(String sessionId, String candidateId, JoiningRequest req, String email) {
        sessionService.getOwnedSession(sessionId, email);
        Candidate candidate = support.lockCandidate(sessionId, candidateId);
        if (candidate.getReviewStatus() != ReviewStatus.ACCEPTED && candidate.getReviewStatus() != ReviewStatus.JOINING_SET) {
            throw ApiException.validation("Cannot set a joining date", "The candidate must have accepted the offer");
        }
        CandidateOffer offer = offerOf(candidateId);

        offer.setJoiningDate(req.joiningDate());
        offer.setJoiningNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
        offerRepository.save(offer);
        User actor = support.getUser(email);
        if (candidate.getReviewStatus() == ReviewStatus.ACCEPTED) {
            support.moveTo(candidate, ReviewStatus.JOINING_SET, "JOINING_SET", actor, "Joining date set to " + req.joiningDate() + " by " + actor.getFullName());
        } else {
            support.recordEvent(candidateId, "JOINING_SET", actor, "Joining details changed to " + req.joiningDate() + " by " + actor.getFullName());
        }
        return CandidateOfferDto.from(offer);
    }

    @Transactional(readOnly = true)
    public String getOfferDownloadUrl(String sessionId, String candidateId, String email) {
        support.sessionForOffer(sessionId, email);
        support.candidateInSession(sessionId, candidateId);
        CandidateOffer offer = offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));
        return uploadService.getPresignedUrl(offer.getFileKey());
    }

    private CandidateOffer offerOf(String candidateId) {
        return offerRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.notFound("Offer not found", "No offer exists for this candidate"));
    }

}

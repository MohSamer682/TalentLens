package com.resumescreener.api.candidate;

import com.resumescreener.api.candidate.dto.ResumeFileDto;
import com.resumescreener.api.session.SessionService;
import com.resumescreener.common.session.CandidateRepository;
import com.resumescreener.common.session.CandidateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final SessionService sessionService;
    private final CandidateRepository candidateRepository;

    public Page<ResumeFileDto> listResumes(String sessionId, CandidateStatus status, Pageable pageable, String email) {
        sessionService.getSession(sessionId, email);
        var page = status == null
                ? candidateRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable)
                : candidateRepository.findBySessionIdAndStatusOrderByCreatedAtDesc(sessionId, status, pageable);
        return page.map(ResumeFileDto::from);
    }
}

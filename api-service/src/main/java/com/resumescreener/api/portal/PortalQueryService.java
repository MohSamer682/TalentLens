package com.resumescreener.api.portal;

import com.resumescreener.api.common.ApiException;
import com.resumescreener.api.portal.dto.ApplicationSummary;
import com.resumescreener.api.portal.dto.ApplicationView;
import com.resumescreener.api.portal.dto.PortalInterview;
import com.resumescreener.api.portal.dto.PortalStage;
import com.resumescreener.api.portal.dto.StageStep;
import com.resumescreener.api.portal.dto.StepState;
import com.resumescreener.common.session.InterviewKind;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The signed-in candidate's applications. Read only; anything not theirs is simply not found. */
@Service
@RequiredArgsConstructor
public class PortalQueryService {

    /** The order rounds usually run in, which is how candidates expect to read them. */
    private static final List<InterviewKind> ROUND_ORDER = List.of(InterviewKind.HR, InterviewKind.TECHNICAL, InterviewKind.MANAGEMENT);

    private final PortalRepository repository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<ApplicationSummary> applications(String email, Pageable pageable) {
        List<ApplicationSummary> page = repository.summaries(email, clock.instant(), pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(page, pageable, repository.count(email));
    }

    @Transactional(readOnly = true)
    public ApplicationView application(String email, String candidateId) {
        PortalRepository.Application application = repository.application(email, candidateId)
                .orElseThrow(() -> ApiException.notFound("Application not found", "No application with this id on your account"));
        List<PortalInterview> interviews = repository.interviews(candidateId);
        return new ApplicationView(application.id(), application.job(), PortalStage.of(application.reviewStatus()),
                steps(application.interviewPlan(), interviews), interviews, application.recruiter());
    }

    /** One step per planned round, plus any round that has an interview without being planned; the latest interview decides the state. */
    static List<StageStep> steps(String plan, List<PortalInterview> interviews) {
        Set<InterviewKind> kinds = new LinkedHashSet<>();
        if (plan != null && !plan.isBlank()) {
            for (String name : plan.split(",")) {
                kinds.add(InterviewKind.valueOf(name.trim()));
            }
        }
        Map<InterviewKind, StepState> latest = new EnumMap<>(InterviewKind.class);
        for (PortalInterview interview : interviews) {
            kinds.add(interview.kind());
            latest.put(interview.kind(), switch (interview.status()) {
                case SCHEDULED -> StepState.SCHEDULED;
                case COMPLETED -> StepState.DONE;
                case CANCELLED -> StepState.CANCELLED;
            });
        }
        List<StageStep> steps = new ArrayList<>();
        for (InterviewKind kind : ROUND_ORDER) {
            if (kinds.contains(kind)) {
                steps.add(new StageStep(kind, latest.getOrDefault(kind, StepState.PLANNED)));
            }
        }
        return steps;
    }
}

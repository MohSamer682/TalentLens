package com.resumescreener.batch.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumescreener.common.llm.LlmScoreDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class LlmScoreParser {

    // Models add extra keys or return a lone string for a list; neither should fail the candidate
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    public LlmScoreDto parseContent(String content) {
        if (content == null || content.isBlank()) {
            throw new LlmException("LLM response content is empty");
        }

        // Reasoning models prepend <think>...</think>, which may itself contain braces
        content = content.replaceAll("(?s)<think>.*?</think>", "");
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new LlmRetryableException("No JSON object found in LLM response");
        }

        String json = content.substring(start, end + 1);
        LlmScoreDto score;
        try {
            score = objectMapper.readValue(json, LlmScoreDto.class);
        } catch (Exception e) {
            throw new LlmRetryableException("Failed to parse LLM JSON: " + e.getMessage(), e);
        }

        validateRange("technical_skills", score.getTechnicalSkills());
        validateRange("experience", score.getExperience());
        validateRange("education", score.getEducation());
        validateRange("soft_skills", score.getSoftSkills());
        validateRange("culture_fit", score.getCultureFit());

        if (score.getSkills() == null) {
            score.setSkills(new ArrayList<>());
        }
        if (isBlank(score.getJustification())) {
            score.setJustification("No justification provided by LLM.");
        }
        if (isBlank(score.getStrengths())) {
            score.setStrengths("No strengths provided by LLM.");
        }
        if (isBlank(score.getGaps())) {
            score.setGaps("No gaps provided by LLM.");
        }

        return score;
    }

    private void validateRange(String field, Integer value) {
        if (value == null) {
            throw new LlmRetryableException("Missing required LLM score field: " + field);
        }
        if (value < 0 || value > 100) {
            throw new LlmRetryableException("LLM score field out of range 0-100: " + field + "=" + value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

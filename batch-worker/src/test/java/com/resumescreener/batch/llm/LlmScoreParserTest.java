package com.resumescreener.batch.llm;

import com.resumescreener.common.dto.LlmScoreDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmScoreParserTest {

    private final LlmScoreParser parser = new LlmScoreParser();

    @Test
    void parsesValidJsonContent() {
        String content = """
                {
                  "technical_skills": 91,
                  "experience": 82,
                  "education": 70,
                  "soft_skills": 76,
                  "culture_fit": 88,
                  "skills": ["Java", "Spring Boot"],
                  "justification": "Strong backend fit.",
                  "strengths": "Solid Java experience.",
                  "gaps": "Limited cloud detail."
                }
                """;

        LlmScoreDto score = parser.parseContent(content);

        assertThat(score.getTechnicalSkills()).isEqualTo(91);
        assertThat(score.getExperience()).isEqualTo(82);
        assertThat(score.getSkills()).containsExactly("Java", "Spring Boot");
    }

    @Test
    void rejectsMissingRequiredScore() {
        String content = """
                {
                  "experience": 82,
                  "education": 70,
                  "soft_skills": 76,
                  "culture_fit": 88,
                  "skills": ["Java"],
                  "justification": "Assessment.",
                  "strengths": "Strength.",
                  "gaps": "Gap."
                }
                """;

        assertThatThrownBy(() -> parser.parseContent(content))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("technical_skills");
    }

    @Test
    void rejectsOutOfRangeScore() {
        String content = """
                {
                  "technical_skills": 101,
                  "experience": 82,
                  "education": 70,
                  "soft_skills": 76,
                  "culture_fit": 88,
                  "skills": ["Java"],
                  "justification": "Assessment.",
                  "strengths": "Strength.",
                  "gaps": "Gap."
                }
                """;

        assertThatThrownBy(() -> parser.parseContent(content))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("technical_skills");
    }

    @Test
    void rejectsContentWithoutJsonObject() {
        assertThatThrownBy(() -> parser.parseContent("I cannot score this resume."))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("No JSON object");
    }

    @Test
    void fillsSafeDefaultsForOptionalNarrativeFields() {
        String content = """
                {
                  "technical_skills": 80,
                  "experience": 75,
                  "education": 60,
                  "soft_skills": 70,
                  "culture_fit": 65
                }
                """;

        LlmScoreDto score = parser.parseContent(content);

        assertThat(score.getSkills()).isEmpty();
        assertThat(score.getJustification()).isEqualTo("No justification provided by LLM.");
        assertThat(score.getStrengths()).isEqualTo("No strengths provided by LLM.");
        assertThat(score.getGaps()).isEqualTo("No gaps provided by LLM.");
    }
}

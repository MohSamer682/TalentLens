package com.resumescreener.batch.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumescreener.common.llm.LlmScoreDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientHttpTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void scorePostsJsonModeChatCompletionRequestAndParsesReturnedScore() throws Exception {
        AtomicReference<JsonNode> capturedRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> handleCompletion(exchange, capturedRequest));
        server.start();

        LlmClient client = new LlmClient(RestClient.builder().build(), new LlmScoreParser());
        setField(client, "apiKey", "test-key");
        setField(client, "model", "test-model");
        setField(client, "baseUrl", "http://localhost:" + server.getAddress().getPort() + "/v1/");

        LlmScoreDto score = client.score("Need Java and Spring Boot", "Built Spring Boot APIs");

        assertThat(score.getTechnicalSkills()).isEqualTo(93);
        assertThat(score.getExperience()).isEqualTo(84);
        assertThat(score.getEducation()).isEqualTo(72);
        assertThat(score.getSoftSkills()).isEqualTo(66);
        assertThat(score.getCultureFit()).isEqualTo(81);
        assertThat(score.getSkills()).containsExactly("Java", "Spring Boot");

        JsonNode request = capturedRequest.get();
        assertThat(request.get("model").asText()).isEqualTo("test-model");
        assertThat(request.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(request.get("response_format").get("type").asText()).isEqualTo("json_object");
        assertThat(request.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(request.get("messages").get(1).get("role").asText()).isEqualTo("user");
        assertThat(request.get("messages").get(1).get("content").asText())
                .contains("Need Java and Spring Boot")
                .contains("Built Spring Boot APIs");
    }

    @Test
    void serverErrorPropagatesAsRetryableException() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange ->
                sendJson(exchange, 500, "{\"error\":\"upstream overloaded\"}"));
        server.start();

        LlmClient client = newClient();

        // Must surface as HttpServerErrorException so resilience4j retry-exceptions match
        assertThatThrownBy(() -> client.score("jd", "resume"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void clientErrorWrappedAsLlmException() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange ->
                sendJson(exchange, 401, "{\"error\":\"bad key\"}"));
        server.start();

        LlmClient client = newClient();

        assertThatThrownBy(() -> client.score("jd", "resume"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("401");
    }

    private LlmClient newClient() throws Exception {
        LlmClient client = new LlmClient(RestClient.builder().build(), new LlmScoreParser());
        setField(client, "apiKey", "test-key");
        setField(client, "model", "test-model");
        setField(client, "baseUrl", "http://localhost:" + server.getAddress().getPort() + "/v1");
        return client;
    }

    private void handleCompletion(HttpExchange exchange, AtomicReference<JsonNode> capturedRequest) throws IOException {
        JsonNode request = objectMapper.readTree(exchange.getRequestBody());
        capturedRequest.set(request);

        if (!"POST".equals(exchange.getRequestMethod())
                || !"Bearer test-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                || !"json_object".equals(request.path("response_format").path("type").asText())) {
            sendJson(exchange, 400, "{\"error\":\"invalid chat completion request\"}");
            return;
        }

        String content = """
                {
                  "technical_skills": 93,
                  "experience": 84,
                  "education": 72,
                  "soft_skills": 66,
                  "culture_fit": 81,
                  "skills": ["Java", "Spring Boot"],
                  "justification": "Strong backend match.",
                  "strengths": "Production API experience.",
                  "gaps": "Could show more architecture depth."
                }
                """;
        String response = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", content)
                ))
        ));
        sendJson(exchange, 200, response);
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void setField(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

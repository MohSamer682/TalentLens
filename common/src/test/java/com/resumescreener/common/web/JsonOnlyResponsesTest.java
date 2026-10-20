package com.resumescreener.common.web;

import org.apache.catalina.valves.ErrorReportValve;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonOnlyResponsesTest {

    private WebServer server;

    @BeforeEach
    void startTomcat() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        // Boot adds a plain ErrorReportValve when stack traces are hidden; the JSON one must still win
        factory.addContextCustomizers(context -> context.getParent().getPipeline().addValve(new ErrorReportValve()));
        new JsonOnlyResponses().jsonTomcatErrors().customize(factory);
        server = factory.getWebServer();
        server.start();
    }

    @AfterEach
    void stopTomcat() {
        server.stop();
    }

    @Test
    void illegalCharacterInPathIsAnsweredWithTheJsonEnvelope() throws IOException {
        String reply = send("GET /api/sessions/a|b HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n");

        assertThat(reply).startsWith("HTTP/1.1 400");
        assertThat(reply).contains("Content-Type: application/json;charset=UTF-8");
        assertThat(reply).contains("{\"success\":false,\"message\":\"Malformed request\",\"error\":{\"code\":\"VALIDATION_ERROR\"");
        assertThat(reply).doesNotContainIgnoringCase("<html");
    }

    @Test
    void badPercentEncodingIsAnsweredWithTheJsonEnvelope() throws IOException {
        String reply = send("GET /api/sessions/%ZZ HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n");

        assertThat(reply).startsWith("HTTP/1.1 400");
        assertThat(reply).contains("\"code\":\"VALIDATION_ERROR\"");
        assertThat(reply).doesNotContainIgnoringCase("<html");
    }

    @Test
    void oversizedHeaderIsAnsweredWithTheJsonEnvelope() throws IOException {
        String reply = send("GET /api/sessions HTTP/1.1\r\nHost: x\r\nX-Big: " + "a".repeat(20_000)
                + "\r\nConnection: close\r\n\r\n");

        assertThat(reply).startsWith("HTTP/1.1 400");
        assertThat(reply).contains("\"success\":false");
        assertThat(reply).doesNotContainIgnoringCase("<html");
    }

    @Test
    void unmappedPathOutsideSpringIsANotFoundEnvelope() throws IOException {
        String reply = send("GET /anything HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n");

        assertThat(reply).startsWith("HTTP/1.1 404");
        assertThat(reply).contains("{\"success\":false,\"message\":\"Endpoint not found\",\"error\":{\"code\":\"NOT_FOUND\"");
    }

    @Test
    void envelopeMapsStatusesToTheApiErrorCodes() {
        assertThat(JsonErrorReportValve.envelope(413)).contains("\"message\":\"Request too large\"", "VALIDATION_ERROR");
        assertThat(JsonErrorReportValve.envelope(405)).contains("\"message\":\"Method not allowed\"");
        assertThat(JsonErrorReportValve.envelope(503)).contains("\"code\":\"INTERNAL_ERROR\"", "\"details\":\"503 Service Unavailable\"");
    }

    private String send(String request) throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

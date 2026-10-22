package com.resumescreener.common.web;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.tomcat.util.ExceptionUtils;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.Writer;

/**
 * Tomcat answers some requests itself, before Spring is reached: illegal characters or bad
 * percent-encoding in the URL, oversized headers, an unparseable request line. Its stock valve
 * renders those as an HTML page; this one writes the same envelope the services use.
 */
public class JsonErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(Request request, Response response, Throwable throwable) {
        int status = response.getStatus();
        if (status < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            Writer writer = response.getReporter();
            if (writer != null) {
                writer.write(envelope(status));
                response.finishResponse();
            }
        } catch (IOException | IllegalStateException e) {
            ExceptionUtils.handleThrowable(e);
        }
    }

    static String envelope(int status) {
        String message = switch (status) {
            case 404 -> "Endpoint not found";
            case 405 -> "Method not allowed";
            case 413 -> "Request too large";
            default -> status < 500 ? "Malformed request" : "Request failed";
        };
        String code = switch (status) {
            case 404 -> "NOT_FOUND";
            case 401 -> "AUTHENTICATION_FAILED";
            case 403 -> "ACCESS_DENIED";
            default -> status < 500 ? "VALIDATION_ERROR" : "INTERNAL_ERROR";
        };
        HttpStatus known = HttpStatus.resolve(status);
        String reason = known == null ? "" : " " + known.getReasonPhrase();
        return "{\"success\":false,\"message\":\"" + message + "\",\"error\":{\"code\":\"" + code
                + "\",\"details\":\"" + status + reason + "\"}}";
    }
}

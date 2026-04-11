package com.iflytek.astron.workflow.engine.integration.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HttpIntegration {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpIntegration() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public HttpCallResponse execute(HttpRequestConfig config) throws Exception {
        String url = buildUrl(config.getUrl(), config.getPathParams(), config.getQueryParams());

        int timeout = config.getTimeout() != null ? config.getTimeout() : 30;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout));

        Map<String, String> headers = config.getHeaders();
        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        if (headers == null || !headers.containsKey("Content-Type")) {
            requestBuilder.header("Content-Type", "application/json");
        }

        switch (config.getMethod().toUpperCase()) {
            case "GET":
                requestBuilder.GET();
                break;
            case "POST":
                if (config.getBody() != null) {
                    String jsonBody = objectMapper.writeValueAsString(config.getBody());
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                } else {
                    requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                }
                break;
            case "PUT":
                if (config.getBody() != null) {
                    String jsonBody = objectMapper.writeValueAsString(config.getBody());
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
                } else {
                    requestBuilder.PUT(HttpRequest.BodyPublishers.noBody());
                }
                break;
            case "DELETE":
                requestBuilder.DELETE();
                break;
            case "PATCH":
                if (config.getBody() != null) {
                    String jsonBody = objectMapper.writeValueAsString(config.getBody());
                    requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody));
                } else {
                    requestBuilder.method("PATCH", HttpRequest.BodyPublishers.noBody());
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + config.getMethod());
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        HttpCallResponse result = new HttpCallResponse();
        result.setStatusCode(response.statusCode());
        result.setBody(response.body());
        result.setHeaders(response.headers().map());
        result.setSuccess(response.statusCode() >= 200 && response.statusCode() < 300);

        return result;
    }

    private String buildUrl(String baseUrl, Map<String, Object> pathParams, Map<String, Object> queryParams) {
        String url = baseUrl;

        if (pathParams != null) {
            for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
                url = url.replace("{" + entry.getKey() + "}",
                        URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
            }
        }

        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder queryBuilder = new StringBuilder();
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                if (queryBuilder.length() > 0) {
                    queryBuilder.append("&");
                }
                queryBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
            }
            url += "?" + queryBuilder.toString();
        }

        return url;
    }

    @lombok.Data
    public static class HttpRequestConfig {
        private String url;
        private String method;
        private Map<String, Object> pathParams;
        private Map<String, Object> queryParams;
        private Map<String, String> headers;
        private Object body;
        private Integer timeout;
    }

    @lombok.Data
    public static class HttpCallResponse {
        private int statusCode;
        private String body;
        private Map<String, List<String>> headers;
        private boolean success;
    }
}
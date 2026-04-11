package com.iflytek.astron.workflow.engine.node.impl.http;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.workflow.engine.constants.NodeExecStatusEnum;
import com.iflytek.astron.workflow.engine.constants.NodeTypeEnum;
import com.iflytek.astron.workflow.engine.domain.NodeRunResult;
import com.iflytek.astron.workflow.engine.domain.NodeState;
import com.iflytek.astron.workflow.engine.integration.http.HttpIntegration;
import com.iflytek.astron.workflow.engine.integration.http.HttpIntegration.HttpCallResponse;
import com.iflytek.astron.workflow.engine.integration.http.HttpIntegration.HttpRequestConfig;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.observability.AgentTracer;
import com.iflytek.astron.workflow.engine.observability.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP Request Node Executor
 * Executes HTTP requests (GET, POST, PUT, DELETE, PATCH) and returns the response
 */
@Slf4j
@Component
public class HttpNodeExecutor extends AbstractNodeExecutor {

    @Autowired
    private HttpIntegration httpIntegration;

    @Override
    public NodeTypeEnum getNodeType() {
        return NodeTypeEnum.HTTP;
    }

    @Override
    protected NodeRunResult executeNode(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Map<String, Object> nodeParam = nodeState.node().getData().getNodeParam();

        String url = getString(nodeParam, "url");
        String method = getString(nodeParam, "method", "GET");
        Integer timeout = getInt(nodeParam, "timeout", 30);

        HttpRequestConfig config = new HttpRequestConfig();
        config.setUrl(url);
        config.setMethod(method);
        config.setTimeout(timeout);

        Map<String, Object> pathParams = getMap(nodeParam, "pathParams");
        Map<String, Object> queryParams = getMap(nodeParam, "queryParams");
        Map<String, String> headers = getStringMap(nodeParam, "headers");
        Object body = nodeParam.get("body");

        config.setPathParams(pathParams);
        config.setQueryParams(queryParams);
        config.setHeaders(headers);
        config.setBody(body);

        long startTime = System.currentTimeMillis();
        try (TraceContext.SpanWrapper span = AgentTracer.startToolSpan("http", method)) {
            span.setAttribute("http.url", url);
            span.setAttribute("http.method", method);

            HttpCallResponse response = httpIntegration.execute(config);

            long latencyMs = System.currentTimeMillis() - startTime;
            span.setAttribute("http.status_code", response.getStatusCode());
            span.setAttribute("http.latency_ms", latencyMs);
            span.setAttribute("http.success", response.isSuccess());

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("statusCode", response.getStatusCode());
            outputs.put("body", response.getBody());
            outputs.put("headers", response.getHeaders());
            outputs.put("success", response.isSuccess());

            if (!response.isSuccess()) {
                outputs.put("error", "HTTP request failed with status " + response.getStatusCode());
            }

            AgentTracer.recordToolCall("http", method, latencyMs, response.isSuccess());

            log.info("HTTP request completed: method={}, url={}, status={}, latency={}ms",
                    method, url, response.getStatusCode(), latencyMs);

            NodeRunResult result = new NodeRunResult();
            result.setOutputs(outputs);
            result.setInputs(inputs);
            result.setStatus(NodeExecStatusEnum.SUCCESS);
            return result;

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("HTTP request failed: method={}, url={}, error={}", method, url, e.getMessage(), e);

            AgentTracer.recordToolCall("http", method, latencyMs, false);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("success", false);
            outputs.put("error", e.getMessage());

            NodeRunResult result = new NodeRunResult();
            result.setOutputs(outputs);
            result.setInputs(inputs);
            result.setStatus(NodeExecStatusEnum.ERR_INTERUPT);
            return result;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        String value = getString(map, key);
        return value != null ? value : defaultValue;
    }

    private Integer getInt(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getStringMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            Map<String, Object> rawMap = (Map<String, Object>) value;
            Map<String, String> result = new HashMap<>();
            rawMap.forEach((k, v) -> {
                if (v != null) {
                    result.put(String.valueOf(k), String.valueOf(v));
                }
            });
            return result;
        }
        return null;
    }
}
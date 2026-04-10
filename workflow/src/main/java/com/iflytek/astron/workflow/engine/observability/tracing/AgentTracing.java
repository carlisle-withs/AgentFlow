package com.iflytek.astron.workflow.engine.observability.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class AgentTracing {

    private static final Logger log = LoggerFactory.getLogger(AgentTracing.class);
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    private static Tracer tracer;

    private AgentTracing() {
    }

    public static void setTracer(Tracer tracer) {
        AgentTracing.tracer = tracer;
    }

    public static Tracer getTracer() {
        return tracer;
    }

    public static <T> T executeWithSpan(String spanName, SpanSupplier<T> operation) {
        return executeWithSpan(spanName, SpanKind.INTERNAL, Attributes.empty(), operation);
    }

    public static <T> T executeWithSpan(String spanName, SpanKind kind, Attributes attributes, SpanSupplier<T> operation) {
        if (tracer == null) {
            try {
                return operation.get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(kind)
                .setParent(Context.current())
                .setAllAttributes(attributes)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            updateMdc(span);
            return operation.get(span);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            span.end();
            clearMdc();
        }
    }

    public static void executeWithSpan(String spanName, SpanConsumer operation) {
        executeWithSpan(spanName, SpanKind.INTERNAL, Attributes.empty(), span -> {
            operation.accept(span);
            return null;
        });
    }

    public static void executeWithSpan(String spanName, Attributes attributes, SpanConsumer operation) {
        executeWithSpan(spanName, SpanKind.INTERNAL, attributes, span -> {
            operation.accept(span);
            return null;
        });
    }

    public static void executeWithSpan(String spanName, SpanKind kind, Attributes attributes, SpanConsumer operation) {
        executeWithSpan(spanName, kind, attributes, span -> {
            operation.accept(span);
            return null;
        });
    }

    public static void recordLlmCall(String modelId, String promptType,
                                     int promptLength, int promptTokens,
                                     int responseTokens, long latencyMs, boolean success) {
        Attributes attributes = Attributes.builder()
                .put("llm.model_id", modelId)
                .put("llm.prompt_type", promptType)
                .put("llm.prompt_length", (long) promptLength)
                .put("llm.prompt_tokens", (long) promptTokens)
                .put("llm.response_tokens", (long) responseTokens)
                .put("llm.latency_ms", latencyMs)
                .put("llm.success", success)
                .put("slow", latencyMs > 1000)
                .put("error", !success)
                .build();

        executeWithSpan("llm.reasoning", SpanKind.CLIENT, attributes, span -> {
            log.debug("LLM call recorded: model={}, latency={}ms, success={}",
                    modelId, latencyMs, success);
        });
    }

    public static void recordToolCall(String toolName, String toolCategory, long latencyMs, boolean success) {
        Attributes attributes = Attributes.builder()
                .put("tool.name", toolName)
                .put("tool.category", toolCategory)
                .put("tool.latency_ms", latencyMs)
                .put("tool.success", success)
                .put("slow", latencyMs > 1000)
                .put("error", !success)
                .build();

        executeWithSpan("tool.execute", SpanKind.CLIENT, attributes, span -> {
            log.debug("Tool call recorded: tool={}, latency={}ms, success={}",
                    toolName, latencyMs, success);
        });
    }

    public static void recordWorkflowNode(String nodeId, String nodeType, long latencyMs, boolean success) {
        Attributes attributes = Attributes.builder()
                .put("workflow.node_id", nodeId)
                .put("workflow.node_type", nodeType)
                .put("workflow.latency_ms", latencyMs)
                .put("workflow.success", success)
                .put("slow", latencyMs > 1000)
                .put("error", !success)
                .build();

        executeWithSpan("workflow.node", SpanKind.INTERNAL, attributes, span -> {
            log.debug("Workflow node recorded: node={}, type={}, latency={}ms",
                    nodeId, nodeType, latencyMs);
        });
    }

    public static void recordAgentStep(int stepNumber, String toolName, boolean success) {
        Attributes attributes = Attributes.builder()
                .put("agent.step_number", (long) stepNumber)
                .put("agent.tool_name", toolName != null ? toolName : "none")
                .put("agent.success", success)
                .build();

        executeWithSpan("agent.step", SpanKind.INTERNAL, attributes, span -> {
            log.debug("Agent step recorded: step={}, tool={}, success={}",
                    stepNumber, toolName, success);
        });
    }

    public static String getTraceId() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getTraceId();
        }
        return null;
    }

    public static String getSpanId() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getSpanId();
        }
        return null;
    }

    public static boolean isTracingEnabled() {
        return tracer != null;
    }

    private static void updateMdc(Span span) {
        try {
            if (span != null && span.getSpanContext().isValid()) {
                MDC.put(MDC_TRACE_ID, span.getSpanContext().getTraceId());
                MDC.put(MDC_SPAN_ID, span.getSpanContext().getSpanId());
            }
        } catch (Exception e) {
            log.warn("Failed to update MDC: {}", e.getMessage());
        }
    }

    private static void clearMdc() {
        try {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        } catch (Exception e) {
            log.warn("Failed to clear MDC: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    public interface SpanSupplier<T> {
        T get(Span span) throws Exception;
    }

    @FunctionalInterface
    public interface SpanConsumer {
        void accept(Span span) throws Exception;
    }

    public static class SpanAttributes {
        public static final AttributeKey<String> LLM_MODEL_ID = AttributeKey.stringKey("llm.model_id");
        public static final AttributeKey<String> LLM_PROMPT_TYPE = AttributeKey.stringKey("llm.prompt_type");
        public static final AttributeKey<Long> LLM_PROMPT_TOKENS = AttributeKey.longKey("llm.prompt_tokens");
        public static final AttributeKey<Long> LLM_RESPONSE_TOKENS = AttributeKey.longKey("llm.response_tokens");
        public static final AttributeKey<Long> LLM_LATENCY_MS = AttributeKey.longKey("llm.latency_ms");

        public static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("tool.name");
        public static final AttributeKey<String> TOOL_CATEGORY = AttributeKey.stringKey("tool.category");
        public static final AttributeKey<Long> TOOL_LATENCY_MS = AttributeKey.longKey("tool.latency_ms");

        public static final AttributeKey<String> WORKFLOW_NODE_ID = AttributeKey.stringKey("workflow.node_id");
        public static final AttributeKey<String> WORKFLOW_NODE_TYPE = AttributeKey.stringKey("workflow.node_type");
        public static final AttributeKey<Long> WORKFLOW_LATENCY_MS = AttributeKey.longKey("workflow.latency_ms");

        public static final AttributeKey<Boolean> SLOW = AttributeKey.booleanKey("slow");
        public static final AttributeKey<Boolean> ERROR = AttributeKey.booleanKey("error");
    }
}

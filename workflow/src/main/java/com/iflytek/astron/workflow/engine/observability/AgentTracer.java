package com.iflytek.astron.workflow.engine.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public class AgentTracer {

    private static final AttributeKey<String> AGENT_NODE_ID = AttributeKey.stringKey("agent.node_id");
    private static final AttributeKey<String> AGENT_NODE_NAME = AttributeKey.stringKey("agent.node_name");
    private static final AttributeKey<String> LLM_MODEL_ID = AttributeKey.stringKey("llm.model_id");
    private static final AttributeKey<String> LLM_PROMPT_TYPE = AttributeKey.stringKey("llm.prompt_type");
    private static final AttributeKey<Long> LLM_PROMPT_TOKENS = AttributeKey.longKey("llm.prompt_tokens");
    private static final AttributeKey<Long> LLM_RESPONSE_TOKENS = AttributeKey.longKey("llm.response_tokens");
    private static final AttributeKey<Long> LLM_LATENCY_MS = AttributeKey.longKey("llm.latency_ms");
    private static final AttributeKey<Boolean> LLM_SUCCESS = AttributeKey.booleanKey("llm.success");
    private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("tool.name");
    private static final AttributeKey<String> TOOL_CATEGORY = AttributeKey.stringKey("tool.category");
    private static final AttributeKey<Long> TOOL_LATENCY_MS = AttributeKey.longKey("tool.latency_ms");
    private static final AttributeKey<Boolean> TOOL_SUCCESS = AttributeKey.booleanKey("tool.success");
    private static final AttributeKey<String> WORKFLOW_NODE_ID = AttributeKey.stringKey("workflow.node_id");
    private static final AttributeKey<String> WORKFLOW_NODE_TYPE = AttributeKey.stringKey("workflow.node_type");
    private static final AttributeKey<Long> WORKFLOW_LATENCY_MS = AttributeKey.longKey("workflow.latency_ms");
    private static final AttributeKey<Boolean> WORKFLOW_SUCCESS = AttributeKey.booleanKey("workflow.success");
    private static final AttributeKey<Boolean> SLOW = AttributeKey.booleanKey("slow");
    private static final AttributeKey<Boolean> ERROR = AttributeKey.booleanKey("error");
    private static final AttributeKey<String> PLANNER_TASK_TYPE = AttributeKey.stringKey("planner.task_type");
    private static final AttributeKey<String> REFLECT_STEP = AttributeKey.stringKey("reflect.step");
    private static final AttributeKey<String> MEMORY_OPERATION = AttributeKey.stringKey("memory.operation");
    private static final AttributeKey<Long> MEMORY_SIZE = AttributeKey.longKey("memory.size");

    private static Tracer tracer;

    private AgentTracer() {
    }

    public static void setTracer(Tracer tracer) {
        AgentTracer.tracer = tracer;
    }

    public static Tracer getTracer() {
        return tracer;
    }

    public static TraceContext.SpanWrapper startAgentSpan(String nodeId, String nodeName) {
        if (tracer == null) {
            return new TraceContext.SpanWrapper("agent.execute", Span.getInvalid(), null);
        }

        Span span = tracer.spanBuilder("agent.execute")
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(Context.current())
                .setAttribute("agent.node_id", nodeId)
                .setAttribute("agent.node_name", nodeName)
                .startSpan();

        return new TraceContext.SpanWrapper("agent.execute", span, span.getSpanContext().getTraceId());
    }

    public static TraceContext.SpanWrapper startLlmSpan(String modelId, String promptType) {
        if (tracer == null) {
            return new TraceContext.SpanWrapper("llm.reasoning", Span.getInvalid(), null);
        }

        Span span = tracer.spanBuilder("llm.reasoning")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(Context.current())
                .setAttribute("llm.model_id", modelId)
                .setAttribute("llm.prompt_type", promptType)
                .startSpan();

        return new TraceContext.SpanWrapper("llm.reasoning", span, span.getSpanContext().getTraceId());
    }

    public static TraceContext.SpanWrapper startToolSpan(String toolName, String toolCategory) {
        if (tracer == null) {
            return new TraceContext.SpanWrapper("tool.execute", Span.getInvalid(), null);
        }

        Span span = tracer.spanBuilder("tool.execute")
                .setSpanKind(SpanKind.CLIENT)
                .setParent(Context.current())
                .setAttribute("tool.name", toolName)
                .setAttribute("tool.category", toolCategory)
                .startSpan();

        return new TraceContext.SpanWrapper("tool.execute", span, span.getSpanContext().getTraceId());
    }

    public static <T> T executeWithSpan(String spanName, Supplier<T> operation) {
        if (tracer == null) {
            return operation.get();
        }

        Span span = tracer.spanBuilder(spanName)
                .setParent(Context.current())
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            return operation.get();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    public static <T> T executeWithSpan(String spanName, SpanKind kind, Attributes attributes, Supplier<T> operation) {
        if (tracer == null) {
            return operation.get();
        }

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(kind)
                .setParent(Context.current())
                .setAllAttributes(attributes)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            return operation.get();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    public static void recordLlmCall(String modelId, String promptType,
                                   int promptLength, int promptTokens,
                                   int responseTokens, long latencyMs, boolean success) {
        Attributes attributes = Attributes.builder()
                .put(LLM_MODEL_ID, modelId)
                .put(LLM_PROMPT_TYPE, promptType)
                .put(LLM_PROMPT_TOKENS, (long) promptTokens)
                .put(LLM_RESPONSE_TOKENS, (long) responseTokens)
                .put(LLM_LATENCY_MS, latencyMs)
                .put(LLM_SUCCESS, success)
                .put(SLOW, latencyMs > 1000)
                .put(ERROR, !success)
                .build();

        executeWithSpan("llm.reasoning", SpanKind.CLIENT, attributes, () -> {
            log.debug("LLM call recorded: model={}, latency={}ms, success={}",
                    modelId, latencyMs, success);
            AgentMetrics.recordLlmCall(modelId, promptLength, promptTokens,
                    responseTokens, latencyMs, success);
            return null;
        });
    }

    public static void recordToolCall(String toolName, String toolCategory, long latencyMs, boolean success) {
        Attributes attributes = Attributes.builder()
                .put(TOOL_NAME, toolName)
                .put(TOOL_CATEGORY, toolCategory)
                .put(TOOL_LATENCY_MS, latencyMs)
                .put(TOOL_SUCCESS, success)
                .put(SLOW, latencyMs > 1000)
                .put(ERROR, !success)
                .build();

        executeWithSpan("tool.execute", SpanKind.CLIENT, attributes, () -> {
            log.debug("Tool call recorded: tool={}, latency={}ms, success={}",
                    toolName, latencyMs, success);
            AgentMetrics.recordPluginCall(toolName, latencyMs, success);
            return null;
        });
    }

    public static void recordWorkflowNode(String nodeId, String nodeType, long latencyMs, boolean success) {
        Attributes attributes = Attributes.builder()
                .put(WORKFLOW_NODE_ID, nodeId)
                .put(WORKFLOW_NODE_TYPE, nodeType)
                .put(WORKFLOW_LATENCY_MS, latencyMs)
                .put(WORKFLOW_SUCCESS, success)
                .put(SLOW, latencyMs > 1000)
                .put(ERROR, !success)
                .build();

        executeWithSpan("workflow.node", SpanKind.INTERNAL, attributes, () -> {
            log.debug("Workflow node recorded: node={}, type={}, latency={}ms",
                    nodeId, nodeType, latencyMs);
            AgentMetrics.recordWorkflowNodeExecution(nodeType, latencyMs);
            return null;
        });
    }

    public static TraceSummary getTraceSummary() {
        TraceSummary summary = new TraceSummary();
        summary.setTraceId(TraceContext.getTraceId());
        summary.setSpanId(TraceContext.getSpanId());
        summary.setMetrics(AgentMetrics.snapshot());
        return summary;
    }

    @lombok.Data
    public static class TraceSummary {
        private String traceId;
        private String spanId;
        private java.util.Map<String, Object> metrics;

        public String toJson() {
            return String.format(
                    "{\"traceId\":\"%s\",\"spanId\":\"%s\",\"metrics\":%s}",
                    traceId, spanId, metrics != null ? metrics.toString() : "{}"
            );
        }
    }
}

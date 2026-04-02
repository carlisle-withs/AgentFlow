package com.iflytek.astron.workflow.engine.observability;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent 全链路追踪器
 * 基于 OpenTelemetry，将 TraceID 贯穿 '请求-推理-执行' 全链路
 *
 * <p>追踪点：
 * <ul>
 *   <li>请求入口 - WorkflowEngine</li>
 *   <li>节点执行 - AgentNodeExecutor</li>
 *   <li>LLM 推理 - ReActLoop/ModelServiceClient</li>
 *   <li>工具执行 - PluginServiceClient</li>
 *   <li>计划生成 - Planner</li>
 *   <li>反思评估 - Reflect</li>
 *   <li>记忆操作 - Memory</li>
 * </ul>
 */
@Slf4j
public class AgentTracer {

    /**
     * 创建 Agent 执行追踪 Span
     */
    public static TraceContext.SpanWrapper startAgentSpan(String nodeId, String nodeName) {
        TraceContext.TraceInfo info = TraceContext.get();
        if (info == null) {
            info = TraceContext.create();
        }

        TraceContext.SpanWrapper span = TraceContext.span("agent.execute");
        span.setAttribute("agent.nodeId", nodeId);
        span.setAttribute("agent.nodeName", nodeName);
        span.setAttribute("agent.traceId", info.getTraceId());

        return span;
    }

    /**
     * 创建 LLM 推理追踪 Span
     */
    public static TraceContext.SpanWrapper startLlmSpan(String modelId, String promptType) {
        TraceContext.SpanWrapper span = TraceContext.span("llm.reasoning");
        span.setAttribute("llm.modelId", modelId);
        span.setAttribute("llm.promptType", promptType);
        return span;
    }

    /**
     * 创建工具执行追踪 Span
     */
    public static TraceContext.SpanWrapper startToolSpan(String toolName, String toolCategory) {
        TraceContext.SpanWrapper span = TraceContext.span("tool.execute");
        span.setAttribute("tool.name", toolName);
        span.setAttribute("tool.category", toolCategory);
        return span;
    }

    /**
     * 创建计划生成追踪 Span
     */
    public static TraceContext.SpanWrapper startPlannerSpan(String taskType) {
        TraceContext.SpanWrapper span = TraceContext.span("planner.generate");
        span.setAttribute("planner.taskType", taskType);
        return span;
    }

    /**
     * 创建反思评估追踪 Span
     */
    public static TraceContext.SpanWrapper startReflectSpan(String stepDescription) {
        TraceContext.SpanWrapper span = TraceContext.span("reflect.evaluate");
        span.setAttribute("reflect.step", stepDescription);
        return span;
    }

    /**
     * 创建记忆操作追踪 Span
     */
    public static TraceContext.SpanWrapper startMemorySpan(String operation, int memorySize) {
        TraceContext.SpanWrapper span = TraceContext.span("memory.operation");
        span.setAttribute("memory.operation", operation);
        span.setAttribute("memory.size", memorySize);
        return span;
    }

    /**
     * 创建工作流节点追踪 Span
     */
    public static TraceContext.SpanWrapper startWorkflowNodeSpan(String nodeId, String nodeType) {
        TraceContext.SpanWrapper span = TraceContext.span("workflow.node");
        span.setAttribute("workflow.nodeId", nodeId);
        span.setAttribute("workflow.nodeType", nodeType);
        return span;
    }

    /**
     * 记录 LLM 调用指标
     */
    public static void recordLlmCall(String modelId, String promptType,
                                      int promptLength, int promptTokens,
                                      int responseTokens, long latencyMs, boolean success) {
        try (TraceContext.SpanWrapper span = startLlmSpan(modelId, promptType)) {
            // 设置指标属性
            span.setAttribute("llm.promptLength", promptLength);
            span.setAttribute("llm.promptTokens", promptTokens);
            span.setAttribute("llm.responseTokens", responseTokens);
            span.setAttribute("llm.latencyMs", latencyMs);
            span.setAttribute("llm.success", success);

            // 记录到指标中心
            AgentMetrics.recordLlmCall(modelId, promptLength, promptTokens,
                    responseTokens, latencyMs, success);
        }
    }

    /**
     * 记录工具调用指标
     */
    public static void recordToolCall(String toolName, String toolCategory, long latencyMs, boolean success) {
        try (TraceContext.SpanWrapper span = startToolSpan(toolName, toolCategory)) {
            span.setAttribute("tool.latencyMs", latencyMs);
            span.setAttribute("tool.success", success);

            AgentMetrics.recordPluginCall(toolName, latencyMs, success);
        }
    }

    /**
     * 获取当前追踪的摘要信息
     */
    public static TraceSummary getTraceSummary() {
        TraceContext.TraceInfo info = TraceContext.get();
        if (info == null) {
            return null;
        }

        TraceSummary summary = new TraceSummary();
        summary.setTraceId(info.getTraceId());
        summary.setSpanId(info.getSpanId());
        summary.setDepth(info.getDepth() != null ? info.getDepth().get() : 0);

        // 获取指标快照
        summary.setMetrics(AgentMetrics.getMetricsForCurrentTrace());

        return summary;
    }

    /**
     * 追踪摘要
     */
    @lombok.Data
    public static class TraceSummary {
        private String traceId;
        private String spanId;
        private int depth;
        private java.util.Map<String, Object> metrics;

        public String toJson() {
            return String.format(
                    "{\"traceId\":\"%s\",\"spanId\":\"%s\",\"depth\":%d,\"metrics\":%s}",
                    traceId, spanId, depth, metrics != null ? metrics.toString() : "{}"
            );
        }
    }
}

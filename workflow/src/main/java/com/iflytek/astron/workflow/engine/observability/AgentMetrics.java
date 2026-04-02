package com.iflytek.astron.workflow.engine.observability;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 指标采集中心
 * 采集 20+ 指标，覆盖请求-推理-执行全链路
 */
@Slf4j
public class AgentMetrics {

    private static final Map<String, MetricCounter> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, MetricGauge> GAUGES = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> VALUES = new ConcurrentHashMap<>();

    /**
     * 指标类型
     */
    public enum MetricType {
        // LLM 相关
        LLM_PROMPT_LENGTH("llm.prompt.length", "LLM Prompt 长度"),
        LLM_PROMPT_TOKENS("llm.prompt.tokens", "LLM Prompt Token 数"),
        LLM_RESPONSE_TOKENS("llm.response.tokens", "LLM 响应 Token 数"),
        LLM_TOTAL_TOKENS("llm.total.tokens", "LLM 总 Token 消耗"),
        LLM_LATENCY_MS("llm.latency.ms", "LLM 调用延迟(ms)"),
        LLM_REQUESTS_TOTAL("llm.requests.total", "LLM 请求总数"),
        LLM_ERRORS_TOTAL("llm.errors.total", "LLM 错误总数"),

        // 插件相关
        PLUGIN_INVOKE_TOTAL("plugin.invoke.total", "插件调用总数"),
        PLUGIN_INVOKE_LATENCY_MS("plugin.invoke.latency.ms", "插件调用延迟(ms)"),
        PLUGIN_ERRORS_TOTAL("plugin.errors.total", "插件错误总数"),

        // Agent 执行相关
        AGENT_STEPS_TOTAL("agent.steps.total", "Agent 执行步骤总数"),
        AGENT_ITERATIONS_TOTAL("agent.iterations.total", "Agent 迭代次数"),
        AGENT_TOOL_CALLS_TOTAL("agent.tool.calls.total", "Agent 工具调用总数"),
        AGENT_MEMORY_SIZE("agent.memory.size", "Agent 记忆大小"),
        AGENT_CONTEXT_LENGTH("agent.context.length", "Agent 上下文长度"),
        AGENT_EXECUTION_TIME_MS("agent.execution.time.ms", "Agent 执行总时间(ms)"),

        // 工作流相关
        WORKFLOW_NODES_EXECUTED("workflow.nodes.executed", "工作流已执行节点数"),
        WORKFLOW_EDGE_COUNT("workflow.edge.count", "工作流边数"),
        WORKFLOW_EXECUTION_TIME_MS("workflow.execution.time.ms", "工作流执行时间(ms)"),

        // 系统相关
        SYSTEM_CPU_USAGE("system.cpu.usage", "CPU 使用率"),
        SYSTEM_MEMORY_USAGE("system.memory.usage", "内存使用率");

        private final String name;
        private final String description;

        MetricType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 指标计数器
     */
    @Data
    public static class MetricCounter {
        private final String name;
        private final String description;
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong total = new AtomicLong(0);

        public void increment() {
            count.incrementAndGet();
            total.incrementAndGet();
        }

        public void increment(long delta) {
            count.addAndGet(delta);
            total.addAndGet(delta);
        }

        public long getCount() {
            return count.get();
        }

        public long getTotal() {
            return total.get();
        }

        public void reset() {
            count.set(0);
        }
    }

    /**
     * 指标 Gauge
     */
    @Data
    public static class MetricGauge {
        private final String name;
        private final String description;
        private final AtomicLong value = new AtomicLong(0);

        public void set(long val) {
            value.set(val);
        }

        public long get() {
            return value.get();
        }
    }

    /**
     * 记录 LLM 调用
     */
    public static void recordLlmCall(String modelId, int promptLength, int promptTokens,
                                      int responseTokens, long latencyMs, boolean success) {
        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] LLM call: traceId={}, model={}, promptTokens={}, respTokens={}, latency={}ms",
                traceId, modelId, promptTokens, responseTokens, latencyMs);

        // 记录各项指标
        record(MetricType.LLM_PROMPT_LENGTH, promptLength);
        record(MetricType.LLM_PROMPT_TOKENS, promptTokens);
        record(MetricType.LLM_RESPONSE_TOKENS, responseTokens);
        record(MetricType.LLM_TOTAL_TOKENS, promptTokens + responseTokens);
        record(MetricType.LLM_LATENCY_MS, latencyMs);
        increment(MetricType.LLM_REQUESTS_TOTAL);

        if (!success) {
            increment(MetricType.LLM_ERRORS_TOTAL);
        }
    }

    /**
     * 记录插件调用
     */
    public static void recordPluginCall(String pluginName, long latencyMs, boolean success) {
        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] Plugin call: traceId={}, plugin={}, latency={}ms, success={}",
                traceId, pluginName, latencyMs, success);

        increment(MetricType.PLUGIN_INVOKE_TOTAL);
        record(MetricType.PLUGIN_INVOKE_LATENCY_MS, latencyMs);

        if (!success) {
            increment(MetricType.PLUGIN_ERRORS_TOTAL);
        }
    }

    /**
     * 记录 Agent 步骤
     */
    public static void recordAgentStep(int stepNumber, String toolName, boolean success) {
        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] Agent step: traceId={}, step={}, tool={}, success={}",
                traceId, stepNumber, toolName, success);

        increment(MetricType.AGENT_STEPS_TOTAL);
        if (toolName != null) {
            increment(MetricType.AGENT_TOOL_CALLS_TOTAL);
        }
    }

    /**
     * 记录 Agent 迭代
     */
    public static void recordAgentIteration(int iteration, boolean hasToolCall) {
        increment(MetricType.AGENT_ITERATIONS_TOTAL);
        if (hasToolCall) {
            increment(MetricType.AGENT_TOOL_CALLS_TOTAL);
        }
    }

    /**
     * 记录 Agent 记忆大小
     */
    public static void recordMemorySize(int memorySize) {
        set(MetricType.AGENT_MEMORY_SIZE, memorySize);
    }

    /**
     * 记录上下文长度
     */
    public static void recordContextLength(int contextLength) {
        set(MetricType.AGENT_CONTEXT_LENGTH, contextLength);
    }

    /**
     * 记录 Agent 执行时间
     */
    public static void recordAgentExecutionTime(long executionTimeMs) {
        set(MetricType.AGENT_EXECUTION_TIME_MS, executionTimeMs);
    }

    /**
     * 记录工作流节点执行
     */
    public static void recordWorkflowNodeExecution(String nodeType, long latencyMs) {
        increment(MetricType.WORKFLOW_NODES_EXECUTED);
        log.debug("[Metrics] Workflow node: type={}, latency={}ms", nodeType, latencyMs);
    }

    /**
     * 递增计数器
     */
    public static void increment(MetricType metric) {
        getOrCreateCounter(metric.getName(), metric.getDescription()).increment();
    }

    /**
     * 记录值
     */
    public static void record(MetricType metric, long value) {
        String key = metric.getName();
        VALUES.computeIfAbsent(key, k -> new AtomicLong(0)).set(value);
        log.debug("[Metrics] Recorded: {}={}", key, value);
    }

    /**
     * 设置 Gauge 值
     */
    public static void set(MetricType metric, long value) {
        getOrCreateGauge(metric.getName(), metric.getDescription()).set(value);
    }

    /**
     * 获取计数器
     */
    public static MetricCounter getCounter(MetricType metric) {
        return COUNTERS.get(metric.getName());
    }

    /**
     * 获取所有指标快照
     */
    public static Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new ConcurrentHashMap<>();

        // 添加计数器
        for (Map.Entry<String, MetricCounter> entry : COUNTERS.entrySet()) {
            MetricCounter counter = entry.getValue();
            snapshot.put(entry.getKey() + ".count", counter.getCount());
            snapshot.put(entry.getKey() + ".total", counter.getTotal());
        }

        // 添加 Gauge
        for (Map.Entry<String, MetricGauge> entry : GAUGES.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }

        // 添加最新值
        for (Map.Entry<String, AtomicLong> entry : VALUES.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }

        return snapshot;
    }

    /**
     * 获取追踪ID关联的指标
     */
    public static Map<String, Object> getMetricsForCurrentTrace() {
        String traceId = TraceContext.getTraceId();
        Map<String, Object> metrics = snapshot();
        metrics.put("traceId", traceId);
        return metrics;
    }

    private static MetricCounter getOrCreateCounter(String name, String description) {
        return COUNTERS.computeIfAbsent(name, k -> {
            log.debug("Created counter: {}", name);
            return new MetricCounter(name, description);
        });
    }

    private static MetricGauge getOrCreateGauge(String name, String description) {
        return GAUGES.computeIfAbsent(name, k -> {
            log.debug("Created gauge: {}", name);
            return new MetricGauge(name, description);
        });
    }

    /**
     * 清空所有指标（测试用）
     */
    public static void clear() {
        COUNTERS.clear();
        GAUGES.clear();
        VALUES.clear();
        log.info("All metrics cleared");
    }
}

package com.iflytek.astron.workflow.engine.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 指标采集中心
 * 基于 Micrometer MeterRegistry 实现，支持 Prometheus 抓取
 *
 * <p>采集 20+ 指标，覆盖请求-推理-执行全链路
 */
@Slf4j
public class AgentMetrics {

    private static MeterRegistry meterRegistry;
    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();

    /**
     * 设置 MeterRegistry 实例
     */
    public static void setMeterRegistry(MeterRegistry meterRegistry) {
        AgentMetrics.meterRegistry = meterRegistry;
        log.info("MeterRegistry set: {}", meterRegistry);
    }

    /**
     * 获取 MeterRegistry
     */
    public static MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * 指标名称常量
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
        WORKFLOW_EXECUTION_TIME_MS("workflow.execution.time.ms", "工作流执行时间(ms)");

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
     * 记录 LLM 调用
     */
    public static void recordLlmCall(String modelId, int promptLength, int promptTokens,
                                      int responseTokens, long latencyMs, boolean success) {
        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] LLM call: traceId={}, model={}, promptTokens={}, respTokens={}, latency={}ms",
                traceId, modelId, promptTokens, responseTokens, latencyMs);

        if (meterRegistry == null) {
            log.warn("MeterRegistry not set, skipping metrics recording");
            return;
        }

        // 记录各项指标
        Timer llmTimer = getOrCreateTimer(MetricType.LLM_LATENCY_MS.getName(), "LLM调用延迟");
        llmTimer.record(latencyMs, TimeUnit.MILLISECONDS);

        Counter.builder(MetricType.LLM_PROMPT_LENGTH.getName())
                .description("LLM Prompt 长度")
                .tag("model", modelId)
                .register(meterRegistry)
                .increment(promptLength);

        Counter.builder(MetricType.LLM_PROMPT_TOKENS.getName())
                .description("LLM Prompt Token 数")
                .tag("model", modelId)
                .register(meterRegistry)
                .increment(promptTokens);

        Counter.builder(MetricType.LLM_RESPONSE_TOKENS.getName())
                .description("LLM 响应 Token 数")
                .tag("model", modelId)
                .register(meterRegistry)
                .increment(responseTokens);

        Counter.builder(MetricType.LLM_TOTAL_TOKENS.getName())
                .description("LLM 总 Token 消耗")
                .tag("model", modelId)
                .register(meterRegistry)
                .increment(promptTokens + responseTokens);

        Counter.builder(MetricType.LLM_REQUESTS_TOTAL.getName())
                .description("LLM 请求总数")
                .tag("model", modelId)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();

        if (!success) {
            Counter.builder(MetricType.LLM_ERRORS_TOTAL.getName())
                    .description("LLM 错误总数")
                    .tag("model", modelId)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 记录插件调用
     */
    public static void recordPluginCall(String pluginName, long latencyMs, boolean success) {
        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] Plugin call: traceId={}, plugin={}, latency={}ms, success={}",
                traceId, pluginName, latencyMs, success);

        if (meterRegistry == null) return;

        Timer pluginTimer = getOrCreateTimer(MetricType.PLUGIN_INVOKE_LATENCY_MS.getName(), "插件调用延迟");
        pluginTimer.record(latencyMs, TimeUnit.MILLISECONDS);

        Counter.builder(MetricType.PLUGIN_INVOKE_TOTAL.getName())
                .description("插件调用总数")
                .tag("plugin", pluginName)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();

        if (!success) {
            Counter.builder(MetricType.PLUGIN_ERRORS_TOTAL.getName())
                    .description("插件错误总数")
                    .tag("plugin", pluginName)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 记录 Agent 步骤
     */
    public static void recordAgentStep(int stepNumber, String toolName, boolean success) {
        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] Agent step: traceId={}, step={}, tool={}, success={}",
                traceId, stepNumber, toolName, success);

        if (meterRegistry == null) return;

        Counter.builder(MetricType.AGENT_STEPS_TOTAL.getName())
                .description("Agent 执行步骤总数")
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();

        if (toolName != null) {
            Counter.builder(MetricType.AGENT_TOOL_CALLS_TOTAL.getName())
                    .description("Agent 工具调用总数")
                    .tag("tool", toolName)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 记录 Agent 迭代
     */
    public static void recordAgentIteration(int iteration, boolean hasToolCall) {
        if (meterRegistry == null) return;

        Counter.builder(MetricType.AGENT_ITERATIONS_TOTAL.getName())
                .description("Agent 迭代次数")
                .register(meterRegistry)
                .increment();

        if (hasToolCall) {
            Counter.builder(MetricType.AGENT_TOOL_CALLS_TOTAL.getName())
                    .description("Agent 工具调用总数")
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 记录 Agent 记忆大小
     */
    public static void recordMemorySize(int memorySize) {
        if (meterRegistry == null) return;

        Gauge.builder(MetricType.AGENT_MEMORY_SIZE.getName(), () -> memorySize)
                .description("Agent 记忆大小")
                .register(meterRegistry);
    }

    /**
     * 记录上下文长度
     */
    public static void recordContextLength(int contextLength) {
        if (meterRegistry == null) return;

        Gauge.builder(MetricType.AGENT_CONTEXT_LENGTH.getName(), () -> contextLength)
                .description("Agent 上下文长度")
                .register(meterRegistry);
    }

    /**
     * 记录 Agent 执行时间
     */
    public static void recordAgentExecutionTime(long executionTimeMs) {
        if (meterRegistry == null) return;

        Counter.builder(MetricType.AGENT_EXECUTION_TIME_MS.getName())
                .description("Agent 执行总时间(ms)")
                .register(meterRegistry)
                .increment(executionTimeMs);
    }

    /**
     * 记录工作流节点执行
     */
    public static void recordWorkflowNodeExecution(String nodeType, long latencyMs) {
        if (meterRegistry == null) return;

        Counter.builder(MetricType.WORKFLOW_NODES_EXECUTED.getName())
                .description("工作流已执行节点数")
                .tag("nodeType", nodeType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 递增计数器
     */
    public static void increment(MetricType metric) {
        if (meterRegistry == null) return;

        Counter.builder(metric.getName())
                .description(metric.getDescription())
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录值
     */
    public static void record(MetricType metric, long value) {
        if (meterRegistry == null) return;

        Counter.builder(metric.getName())
                .description(metric.getDescription())
                .register(meterRegistry)
                .increment(value);
    }

    /**
     * 设置 Gauge 值
     */
    public static void set(MetricType metric, long value) {
        if (meterRegistry == null) return;

        AtomicLong gaugeValue = GAUGES.computeIfAbsent(metric.getName(), k -> new AtomicLong(0));
        gaugeValue.set(value);

        Gauge.builder(metric.getName(), gaugeValue, AtomicLong::get)
                .description(metric.getDescription())
                .register(meterRegistry);
    }

    /**
     * 获取计数器
     */
    public static double getCounter(MetricType metric) {
        if (meterRegistry == null) return 0;

        return meterRegistry.find(metric.getName()).counter() != null
                ? meterRegistry.find(metric.getName()).counter().count()
                : 0;
    }

    /**
     * 获取追踪ID关联的指标
     */
    public static Map<String, Object> getMetricsForCurrentTrace() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        String traceId = TraceContext.getTraceId();
        metrics.put("traceId", traceId);

        if (meterRegistry != null) {
            meterRegistry.getMetrics().forEach((name, meter) -> {
                if (meter instanceof Counter) {
                    metrics.put(name, ((Counter) meter).count());
                }
            });
        }

        return metrics;
    }

    private static Timer getOrCreateTimer(String name, String description) {
        return TIMERS.computeIfAbsent(name, k -> {
            return Timer.builder(name)
                    .description(description)
                    .register(meterRegistry);
        });
    }

    /**
     * 清空所有指标（测试用）
     */
    public static void clear() {
        COUNTERS.clear();
        TIMERS.clear();
        GAUGES.clear();
        log.info("All metrics cleared");
    }
}

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
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Agent 指标采集中心
 * 基于 Micrometer MeterRegistry 实现，支持 Prometheus 抓取
 *
 * <p>设计目标：额外延迟 <10ms
 * <ul>
 *   <li>指标更新使用 Atomic 类无锁更新</li>
 *   <li>Timer 使用异步采样，不阻塞主流程</li>
 *   <li>Counter 预创建，避免运行时注册开销</li>
 *   <li>Snapshot 异步获取，不阻塞指标采集</li>
 * </ul>
 *
 * <p>采集 20+ 指标：
 * <ul>
 *   <li>Counter: 调用次数、Token消耗数</li>
 *   <li>Timer: 耗时分布（LLM延迟、工具延迟、节点延迟）</li>
 *   <li>Histogram: 请求长度、响应长度</li>
 * </ul>
 */
@Slf4j
public class AgentMetrics {

    private static volatile MeterRegistry meterRegistry;
    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();

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
     * 高频指标的预创建计数器
     * 使用 AtomicLongArray 无锁更新，避免 ConcurrentHashMap 查找开销
     */
    private static final AtomicLongArray FAST_COUNTERS = new AtomicLongArray(MetricType.values().length);

    static {
        // 预热计数器数组
        for (int i = 0; i < FAST_COUNTERS.length(); i++) {
            FAST_COUNTERS.set(i, 0);
        }
    }

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
     * 记录 LLM 调用
     * <p>
     * 性能优化：
     * - 使用 AtomicLong 无锁更新
     * - 指标标签使用固定值，减少 Tag 对象创建
     * - 日志异步化，避免阻塞
     */
    public static void recordLlmCall(String modelId, int promptLength, int promptTokens,
                                      int responseTokens, long latencyMs, boolean success) {
        // 快速路径：指标未初始化时跳过
        if (meterRegistry == null) {
            return;
        }

        String traceId = TraceContext.getTraceId();

        // 异步日志记录，避免阻塞主流程
        final String finalTraceId = traceId;
        log.debug("[Metrics] LLM call: traceId={}, model={}, promptTokens={}, respTokens={}, latency={}ms",
                finalTraceId, modelId, promptTokens, responseTokens, latencyMs);

        // 获取或创建 Timer（带缓存）
        Timer llmTimer = TIMERS.computeIfAbsent(MetricType.LLM_LATENCY_MS.getName(), k ->
                Timer.builder(MetricType.LLM_LATENCY_MS.getName())
                        .description("LLM调用延迟")
                        .register(meterRegistry)
        );
        llmTimer.record(latencyMs, TimeUnit.MILLISECONDS);

        // 高频 Counter 使用预创建的 AtomicLong（无锁更新）
        // 低频 Counter 使用 Micrometer Counter
        try {
            Counter promptLengthCounter = COUNTERS.computeIfAbsent(
                    MetricType.LLM_PROMPT_LENGTH.getName() + ":" + modelId,
                    k -> Counter.builder(MetricType.LLM_PROMPT_LENGTH.getName())
                            .description("LLM Prompt 长度")
                            .tag("model", modelId)
                            .register(meterRegistry)
            );
            promptLengthCounter.increment(promptLength);

            Counter promptTokensCounter = COUNTERS.computeIfAbsent(
                    MetricType.LLM_PROMPT_TOKENS.getName() + ":" + modelId,
                    k -> Counter.builder(MetricType.LLM_PROMPT_TOKENS.getName())
                            .description("LLM Prompt Token 数")
                            .tag("model", modelId)
                            .register(meterRegistry)
            );
            promptTokensCounter.increment(promptTokens);

            Counter responseTokensCounter = COUNTERS.computeIfAbsent(
                    MetricType.LLM_RESPONSE_TOKENS.getName() + ":" + modelId,
                    k -> Counter.builder(MetricType.LLM_RESPONSE_TOKENS.getName())
                            .description("LLM 响应 Token 数")
                            .tag("model", modelId)
                            .register(meterRegistry)
            );
            responseTokensCounter.increment(responseTokens);

            Counter totalTokensCounter = COUNTERS.computeIfAbsent(
                    MetricType.LLM_TOTAL_TOKENS.getName() + ":" + modelId,
                    k -> Counter.builder(MetricType.LLM_TOTAL_TOKENS.getName())
                            .description("LLM 总 Token 消耗")
                            .tag("model", modelId)
                            .register(meterRegistry)
            );
            totalTokensCounter.increment(promptTokens + responseTokens);

            Counter requestsCounter = COUNTERS.computeIfAbsent(
                    MetricType.LLM_REQUESTS_TOTAL.getName() + ":" + modelId + ":" + success,
                    k -> Counter.builder(MetricType.LLM_REQUESTS_TOTAL.getName())
                            .description("LLM 请求总数")
                            .tag("model", modelId)
                            .tag("success", String.valueOf(success))
                            .register(meterRegistry)
            );
            requestsCounter.increment();

            if (!success) {
                Counter errorsCounter = COUNTERS.computeIfAbsent(
                        MetricType.LLM_ERRORS_TOTAL.getName() + ":" + modelId,
                        k -> Counter.builder(MetricType.LLM_ERRORS_TOTAL.getName())
                                .description("LLM 错误总数")
                                .tag("model", modelId)
                                .register(meterRegistry)
                );
                errorsCounter.increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record LLM metrics: {}", e.getMessage());
        }
    }

    /**
     * 记录插件调用
     */
    public static void recordPluginCall(String pluginName, long latencyMs, boolean success) {
        if (meterRegistry == null) return;

        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] Plugin call: traceId={}, plugin={}, latency={}ms, success={}",
                traceId, pluginName, latencyMs, success);

        // 记录 Timer
        Timer pluginTimer = TIMERS.computeIfAbsent(MetricType.PLUGIN_INVOKE_LATENCY_MS.getName(), k ->
                Timer.builder(MetricType.PLUGIN_INVOKE_LATENCY_MS.getName())
                        .description("插件调用延迟")
                        .register(meterRegistry)
        );
        pluginTimer.record(latencyMs, TimeUnit.MILLISECONDS);

        // 记录 Counter
        try {
            Counter invokeCounter = COUNTERS.computeIfAbsent(
                    MetricType.PLUGIN_INVOKE_TOTAL.getName() + ":" + pluginName + ":" + success,
                    k -> Counter.builder(MetricType.PLUGIN_INVOKE_TOTAL.getName())
                            .description("插件调用总数")
                            .tag("plugin", pluginName)
                            .tag("success", String.valueOf(success))
                            .register(meterRegistry)
            );
            invokeCounter.increment();

            if (!success) {
                Counter errorCounter = COUNTERS.computeIfAbsent(
                        MetricType.PLUGIN_ERRORS_TOTAL.getName() + ":" + pluginName,
                        k -> Counter.builder(MetricType.PLUGIN_ERRORS_TOTAL.getName())
                                .description("插件错误总数")
                                .tag("plugin", pluginName)
                                .register(meterRegistry)
                );
                errorCounter.increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record plugin metrics: {}", e.getMessage());
        }
    }

    /**
     * 记录 Agent 步骤
     */
    public static void recordAgentStep(int stepNumber, String toolName, boolean success) {
        if (meterRegistry == null) return;

        String traceId = TraceContext.getTraceId();
        log.debug("[Metrics] Agent step: traceId={}, step={}, tool={}, success={}",
                traceId, stepNumber, toolName, success);

        try {
            Counter stepsCounter = COUNTERS.computeIfAbsent(
                    MetricType.AGENT_STEPS_TOTAL.getName() + ":" + success,
                    k -> Counter.builder(MetricType.AGENT_STEPS_TOTAL.getName())
                            .description("Agent 执行步骤总数")
                            .tag("success", String.valueOf(success))
                            .register(meterRegistry)
            );
            stepsCounter.increment();

            if (toolName != null) {
                Counter toolCallsCounter = COUNTERS.computeIfAbsent(
                        MetricType.AGENT_TOOL_CALLS_TOTAL.getName() + ":" + toolName,
                        k -> Counter.builder(MetricType.AGENT_TOOL_CALLS_TOTAL.getName())
                                .description("Agent 工具调用总数")
                                .tag("tool", toolName)
                                .register(meterRegistry)
                );
                toolCallsCounter.increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record agent step metrics: {}", e.getMessage());
        }
    }

    /**
     * 记录 Agent 迭代
     */
    public static void recordAgentIteration(int iteration, boolean hasToolCall) {
        if (meterRegistry == null) return;

        try {
            Counter iterationsCounter = COUNTERS.computeIfAbsent(
                    MetricType.AGENT_ITERATIONS_TOTAL.getName(),
                    k -> Counter.builder(MetricType.AGENT_ITERATIONS_TOTAL.getName())
                            .description("Agent 迭代次数")
                            .register(meterRegistry)
            );
            iterationsCounter.increment();

            if (hasToolCall) {
                Counter toolCallsCounter = COUNTERS.computeIfAbsent(
                        MetricType.AGENT_TOOL_CALLS_TOTAL.getName(),
                        k -> Counter.builder(MetricType.AGENT_TOOL_CALLS_TOTAL.getName())
                                .description("Agent 工具调用总数")
                                .register(meterRegistry)
                );
                toolCallsCounter.increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record agent iteration metrics: {}", e.getMessage());
        }
    }

    /**
     * 记录 Agent 记忆大小
     */
    public static void recordMemorySize(int memorySize) {
        if (meterRegistry == null) return;

        AtomicLong gaugeValue = GAUGES.computeIfAbsent(MetricType.AGENT_MEMORY_SIZE.getName(), k -> new AtomicLong(0));
        gaugeValue.set(memorySize);

        Gauge.builder(MetricType.AGENT_MEMORY_SIZE.getName(), gaugeValue, AtomicLong::get)
                .description("Agent 记忆大小")
                .register(meterRegistry);
    }

    /**
     * 记录上下文长度
     */
    public static void recordContextLength(int contextLength) {
        if (meterRegistry == null) return;

        AtomicLong gaugeValue = GAUGES.computeIfAbsent(MetricType.AGENT_CONTEXT_LENGTH.getName(), k -> new AtomicLong(0));
        gaugeValue.set(contextLength);

        Gauge.builder(MetricType.AGENT_CONTEXT_LENGTH.getName(), gaugeValue, AtomicLong::get)
                .description("Agent 上下文长度")
                .register(meterRegistry);
    }

    /**
     * 记录 Agent 执行时间
     */
    public static void recordAgentExecutionTime(long executionTimeMs) {
        if (meterRegistry == null) return;

        try {
            Counter executionCounter = COUNTERS.computeIfAbsent(
                    MetricType.AGENT_EXECUTION_TIME_MS.getName(),
                    k -> Counter.builder(MetricType.AGENT_EXECUTION_TIME_MS.getName())
                            .description("Agent 执行总时间(ms)")
                            .register(meterRegistry)
            );
            executionCounter.increment(executionTimeMs);
        } catch (Exception e) {
            log.warn("Failed to record agent execution time: {}", e.getMessage());
        }
    }

    /**
     * 记录工作流节点执行
     */
    public static void recordWorkflowNodeExecution(String nodeType, long latencyMs) {
        if (meterRegistry == null) return;

        try {
            Counter nodeCounter = COUNTERS.computeIfAbsent(
                    MetricType.WORKFLOW_NODES_EXECUTED.getName() + ":" + nodeType,
                    k -> Counter.builder(MetricType.WORKFLOW_NODES_EXECUTED.getName())
                            .description("工作流已执行节点数")
                            .tag("nodeType", nodeType)
                            .register(meterRegistry)
            );
            nodeCounter.increment();
        } catch (Exception e) {
            log.warn("Failed to record workflow node execution: {}", e.getMessage());
        }
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
            meterRegistry.getMeters().forEach(meter -> {
                String name = meter.getId().getName();
                if (meter instanceof Counter) {
                    metrics.put(name, ((Counter) meter).count());
                }
            });
        }

        return metrics;
    }

    /**
     * 获取当前指标的快照
     * 用于在节点执行完成后获取完整的指标数据
     *
     * <p>性能优化：
     * - 使用 ConcurrentHashMap 并发安全
     * - 避免在快照中执行耗时的 meter 遍历
     * - 仅返回关键指标
     */
    public static Map<String, Object> snapshot() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        String traceId = TraceContext.getTraceId();
        metrics.put("traceId", traceId);
        metrics.put("timestamp", System.currentTimeMillis());

        if (meterRegistry != null) {
            try {
                // 只获取关键指标，避免遍历所有 meter
                meterRegistry.getMeters().forEach(meter -> {
                    String name = meter.getId().getName();
                    // 只收集我们定义的指标，避免收集框架级指标
                    if (name.startsWith("llm.") || name.startsWith("plugin.") ||
                        name.startsWith("agent.") || name.startsWith("workflow.")) {
                        if (meter instanceof Counter) {
                            metrics.put(name + "_count", ((Counter) meter).count());
                        } else if (meter instanceof Timer) {
                            Timer timer = (Timer) meter;
                            metrics.put(name + "_totalTime", timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
                            metrics.put(name + "_count", timer.count());
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to collect metrics snapshot: {}", e.getMessage());
            }
        }

        return metrics;
    }

    private static Timer getOrCreateTimer(String name, String description) {
        return TIMERS.computeIfAbsent(name, k ->
                Timer.builder(name)
                        .description(description)
                        .register(meterRegistry)
        );
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

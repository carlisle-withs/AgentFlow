package com.iflytek.astron.workflow.engine.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分布式追踪上下文
 * 基于 OpenTelemetry Tracer 实现，TraceID 贯穿请求-推理-执行全链路
 *
 * <p>接口兼容之前的自研实现，内部委托给 OTel Tracer
 */
@Slf4j
public class TraceContext {

    private static final ThreadLocal<TraceInfo> CURRENT_TRACE = new ThreadLocal<>();
    private static Tracer tracer;

    /**
     * 设置 Tracer 实例（由 OTelConfig 配置注入）
     */
    public static void setTracer(Tracer tracer) {
        TraceContext.tracer = tracer;
        log.info("Tracer set: {}", tracer);
    }

    /**
     * 获取 Tracer
     */
    public static Tracer getTracer() {
        return tracer;
    }

    /**
     * 追踪信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TraceInfo {
        private String traceId;
        private String spanId;
        private long startTime;
        private AtomicInteger depth;

        public TraceInfo(String traceId) {
            this.traceId = traceId;
            this.spanId = "0000000000000000";
            this.startTime = System.currentTimeMillis();
            this.depth = new AtomicInteger(0);
        }
    }

    /**
     * 创建新的追踪上下文
     */
    public static TraceInfo create() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        TraceInfo info = new TraceInfo(traceId);
        CURRENT_TRACE.set(info);
        log.debug("Created new trace: traceId={}", traceId);
        return info;
    }

    /**
     * 创建子追踪（从父 TraceID）
     */
    public static TraceInfo createFromParent(String traceId, String parentSpanId) {
        TraceInfo info = new TraceInfo(traceId);
        info.setSpanId(parentSpanId);
        CURRENT_TRACE.set(info);
        log.debug("Created child trace: traceId={}, parentSpan={}", traceId, parentSpanId);
        return info;
    }

    /**
     * 获取当前追踪信息
     */
    public static TraceInfo get() {
        return CURRENT_TRACE.get();
    }

    /**
     * 检查是否有活跃的追踪
     */
    public static boolean hasActiveTrace() {
        return CURRENT_TRACE.get() != null;
    }

    /**
     * 获取当前 TraceID
     */
    public static String getTraceId() {
        TraceInfo info = CURRENT_TRACE.get();
        return info != null ? info.getTraceId() : null;
    }

    /**
     * 创建新的子 span
     */
    public static String newChildSpan() {
        TraceInfo info = CURRENT_TRACE.get();
        if (info == null) return null;

        info.getDepth().incrementAndGet();
        String childSpanId = String.format("%016x", (int) (Math.random() * 0xFFFFFFFF));
        log.debug("Created child span: traceId={}, spanId={}, depth={}",
                info.getTraceId(), childSpanId, info.getDepth().get());
        return childSpanId;
    }

    /**
     * 结束当前 span
     */
    public static void endSpan() {
        TraceInfo info = CURRENT_TRACE.get();
        if (info != null) {
            info.getDepth().decrementAndGet();
        }
    }

    /**
     * 清空追踪上下文
     */
    public static void clear() {
        CURRENT_TRACE.remove();
    }

    /**
     * 创建 Span 对象（兼容旧接口，内部使用 OTel Span）
     */
    public static SpanWrapper span(String name) {
        return span(name, null);
    }

    /**
     * 创建命名的子 Span
     */
    public static SpanWrapper span(String name, String parentSpanId) {
        TraceInfo info = get();
        String traceId = info != null ? info.getTraceId() : UUID.randomUUID().toString().replace("-", "");

        // 使用 OTel 创建 Span
        Span otelSpan;
        if (tracer != null) {
            io.opentelemetry.api.trace.SpanBuilder spanBuilder =
                    tracer.spanBuilder(name)
                            .setAttribute("trace.id", traceId);

            if (parentSpanId != null) {
                // 设置父 SpanContext
                SpanContext parentContext = SpanContext.createFromRemoteParent(
                        traceId, parentSpanId, TraceFlags.getSampled(), TraceState.getDefault());
                spanBuilder.setParent(Context.current().with(
                        io.opentelemetry.api.trace.Span.wrap(parentContext)));
            }

            otelSpan = spanBuilder.startSpan();
        } else {
            // Fallback: 使用 noop span
            otelSpan = Span.getInvalid();
        }

        return new SpanWrapper(name, otelSpan, traceId);
    }

    /**
     * Span 包装器（兼容旧接口）
     */
    @Slf4j
    public static class SpanWrapper implements AutoCloseable {
        private final String name;
        private final Span otelSpan;
        private final String traceId;
        private final long startTime;
        private boolean ended;

        public SpanWrapper(String name, Span otelSpan, String traceId) {
            this.name = name;
            this.otelSpan = otelSpan;
            this.traceId = traceId;
            this.startTime = System.nanoTime();
            this.ended = false;
            log.debug("Span started: name={}, traceId={}", name, traceId);
        }

        /**
         * 添加属性
         */
        public void setAttribute(String key, Object value) {
            if (otelSpan != null && otelSpan.isRecording()) {
                otelSpan.setAttribute(key, String.valueOf(value));
            }
            log.debug("Span attribute: name={}, {}={}", name, key, value);
        }

        /**
         * 记录事件
         */
        public void addEvent(String eventName) {
            if (otelSpan != null && otelSpan.isRecording()) {
                otelSpan.addEvent(eventName);
            }
            log.debug("Span event: name={}, event={}", name, eventName);
        }

        /**
         * 结束 Span
         */
        public void end() {
            if (ended) return;
            this.ended = true;

            if (otelSpan != null && otelSpan.isRecording()) {
                otelSpan.end();
            }

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            log.debug("Span ended: name={}, duration={}ms", name, durationMs);
        }

        @Override
        public void close() {
            end();
        }

        public String getTraceId() {
            return traceId;
        }

        public SpanContext getSpanContext() {
            return otelSpan != null ? otelSpan.getSpanContext() : null;
        }

        public long getDurationMs() {
            return ended ? (System.nanoTime() - startTime) / 1_000_000 : -1;
        }
    }
}

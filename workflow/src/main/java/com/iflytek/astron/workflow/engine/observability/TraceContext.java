package com.iflytek.astron.workflow.engine.observability;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * 分布式追踪上下文
 * 管理 TraceID 在请求-推理-执行全链路的传递
 */
@Slf4j
public class TraceContext {

    private static final ThreadLocal<TraceInfo> CURRENT_TRACE = new ThreadLocal<>();

    /**
     * 追踪信息
     */
    @Data
    public static class TraceInfo {
        private String traceId;
        private String spanId;
        private long startTime;
        private int depth;

        public TraceInfo() {
            this.traceId = generateTraceId();
            this.spanId = "0000000000000000";
            this.startTime = System.currentTimeMillis();
            this.depth = 0;
        }

        public TraceInfo(String traceId, String parentSpanId) {
            this.traceId = traceId;
            this.spanId = parentSpanId;
            this.startTime = System.currentTimeMillis();
            this.depth = 0;
        }

        /**
         * 生成子 span ID
         */
        public String nextSpanId() {
            return String.format("%016x", (int) (Math.random() * 0xFFFFFFFF));
        }
    }

    /**
     * 创建新的追踪上下文
     */
    public static TraceInfo create() {
        TraceInfo info = new TraceInfo();
        CURRENT_TRACE.set(info);
        log.debug("Created new trace: traceId={}", info.getTraceId());
        return info;
    }

    /**
     * 创建子追踪（从父 TraceID）
     */
    public static TraceInfo createFromParent(String traceId, String parentSpanId) {
        TraceInfo info = new TraceInfo(traceId, parentSpanId);
        CURRENT_TRACE.set(info);
        log.debug("Created child trace: traceId={}, parentSpan={}", traceId, parentSpanId);
        return info;
    }

    /**
     * 获取当前追踪信息
     */
    public static TraceInfo get() {
        TraceInfo info = CURRENT_TRACE.get();
        if (info == null) {
            info = create();
        }
        return info;
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
        TraceInfo info = get();
        return info != null ? info.getTraceId() : null;
    }

    /**
     * 创建新的子 span
     */
    public static String newChildSpan() {
        TraceInfo info = get();
        if (info == null) return null;

        info.setDepth(info.getDepth() + 1);
        String childSpanId = info.nextSpanId();
        log.debug("Created child span: traceId={}, spanId={}, depth={}",
                info.getTraceId(), childSpanId, info.getDepth());
        return childSpanId;
    }

    /**
     * 结束当前 span
     */
    public static void endSpan() {
        TraceInfo info = CURRENT_TRACE.get();
        if (info != null) {
            info.setDepth(Math.max(0, info.getDepth() - 1));
        }
    }

    /**
     * 清空追踪上下文
     */
    public static void clear() {
        CURRENT_TRACE.remove();
    }

    /**
     * 生成 TraceID
     */
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 创建 Span 对象
     */
    public static Span span(String name) {
        return new Span(name);
    }

    /**
     * 创建命名的子 Span
     */
    public static Span span(String name, String parentSpanId) {
        return new Span(name, parentSpanId);
    }

    /**
     * Span 生命周期管理
     */
    @Slf4j
    public static class Span implements AutoCloseable {
        private final String name;
        private final String spanId;
        private final String parentSpanId;
        private final String traceId;
        private final long startTime;
        private long endTime;
        private boolean ended;

        public Span(String name) {
            TraceInfo info = get();
            this.name = name;
            this.traceId = info != null ? info.getTraceId() : generateTraceId();
            this.parentSpanId = info != null ? info.getSpanId() : null;
            this.spanId = String.format("%016x", (int) (Math.random() * 0xFFFFFFFF));
            this.startTime = System.nanoTime();
            this.ended = false;

            if (info != null) {
                info.setSpanId(this.spanId);
            }

            log.debug("Span started: name={}, traceId={}, spanId={}", name, traceId, spanId);
        }

        public Span(String name, String parentSpanId) {
            this(name);
            // 直接设置父 span ID
        }

        /**
         * 添加属性
         */
        public void setAttribute(String key, Object value) {
            log.debug("Span attribute: name={}, {}={}", name, key, value);
        }

        /**
         * 记录事件
         */
        public void addEvent(String eventName) {
            log.debug("Span event: name={}, event={}", name, eventName);
        }

        /**
         * 结束 Span
         */
        public void end() {
            if (ended) return;
            this.endTime = System.nanoTime();
            this.ended = true;

            long durationMs = (endTime - startTime) / 1_000_000;
            log.debug("Span ended: name={}, duration={}ms", name, durationMs);
        }

        @Override
        public void close() {
            end();
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public long getDurationMs() {
            return ended ? (endTime - startTime) / 1_000_000 : -1;
        }
    }
}

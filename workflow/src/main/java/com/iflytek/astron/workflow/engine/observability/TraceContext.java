package com.iflytek.astron.workflow.engine.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
public final class TraceContext {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    private static final ThreadLocal<TraceInfo> CURRENT_TRACE = new ThreadLocal<>();
    private static Tracer tracer;

    private TraceContext() {
    }

    public static void setTracer(Tracer tracer) {
        TraceContext.tracer = tracer;
    }

    public static Tracer getTracer() {
        return tracer;
    }

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

        public String getTraceId() { return traceId; }
        public void setTraceId(String traceId) { this.traceId = traceId; }
        public String getSpanId() { return spanId; }
        public void setSpanId(String spanId) { this.spanId = spanId; }
        public long getStartTime() { return startTime; }
        public AtomicInteger getDepth() { return depth; }
    }

    public static TraceInfo create() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        TraceInfo info = new TraceInfo(traceId);
        CURRENT_TRACE.set(info);
        log.debug("Created new trace: traceId={}", traceId);
        return info;
    }

    public static TraceInfo get() {
        return CURRENT_TRACE.get();
    }

    public static String getTraceId() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getTraceId();
        }
        TraceInfo info = CURRENT_TRACE.get();
        return info != null ? info.getTraceId() : null;
    }

    public static String getSpanId() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getSpanId();
        }
        return null;
    }

    public static SpanContext getSpanContext() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext();
        }
        return null;
    }

    public static boolean hasActiveTrace() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return true;
        }
        return CURRENT_TRACE.get() != null;
    }

    public static void clear() {
        try {
            CURRENT_TRACE.remove();
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        } catch (Exception e) {
            log.warn("Failed to clear MDC: {}", e.getMessage());
        }
    }

    public static void updateMdc() {
        Span currentSpan = Span.current();
        try {
            if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
                MDC.put(MDC_TRACE_ID, currentSpan.getSpanContext().getTraceId());
                MDC.put(MDC_SPAN_ID, currentSpan.getSpanContext().getSpanId());
            } else {
                TraceInfo info = CURRENT_TRACE.get();
                if (info != null) {
                    MDC.put(MDC_TRACE_ID, info.getTraceId());
                    MDC.put(MDC_SPAN_ID, info.getSpanId());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update MDC: {}", e.getMessage());
        }
    }

    public static <T> T executeWithSpan(String spanName, Supplier<T> operation) {
        if (tracer == null) {
            return operation.get();
        }

        Span span = tracer.spanBuilder(spanName)
                .setParent(Context.current())
                .startSpan();

        try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
            updateMdc();
            return operation.get();
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
            clear();
        }
    }

    public static void executeWithSpan(String spanName, Runnable operation) {
        executeWithSpan(spanName, () -> {
            operation.run();
            return null;
        });
    }

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

        public void setAttribute(String key, Object value) {
            if (otelSpan != null && otelSpan.isRecording()) {
                otelSpan.setAttribute(key, String.valueOf(value));
            }
            log.debug("Span attribute: name={}, {}={}", name, key, value);
        }

        public void addEvent(String eventName) {
            if (otelSpan != null && otelSpan.isRecording()) {
                otelSpan.addEvent(eventName);
            }
            log.debug("Span event: name={}, event={}", name, eventName);
        }

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

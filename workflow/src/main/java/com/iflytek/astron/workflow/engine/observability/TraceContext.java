package com.iflytek.astron.workflow.engine.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.function.Supplier;

@Slf4j
public final class TraceContext {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    private static Tracer tracer;

    private TraceContext() {
    }

    public static void setTracer(Tracer tracer) {
        TraceContext.tracer = tracer;
    }

    public static Tracer getTracer() {
        return tracer;
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

    public static SpanContext getSpanContext() {
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext();
        }
        return null;
    }

    public static boolean hasActiveTrace() {
        Span currentSpan = Span.current();
        return currentSpan != null && currentSpan.getSpanContext().isValid();
    }

    public static void clear() {
        try {
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
}

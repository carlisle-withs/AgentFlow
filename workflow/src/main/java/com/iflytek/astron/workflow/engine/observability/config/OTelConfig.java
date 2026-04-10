package com.iflytek.astron.workflow.engine.observability.config;

import com.iflytek.astron.workflow.engine.observability.AgentMetrics;
import com.iflytek.astron.workflow.engine.observability.tracing.AgentTracing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.api.common.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class OTelConfig {

    @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${otel.service.name:agentflow-workflow}")
    private String serviceName;

    @Value("${otel.trace.enabled:true}")
    private boolean traceEnabled;

    @Value("${otel.metrics.enabled:true}")
    private boolean metricsEnabled;

    @Value("${otel.trace.sample-rate:0.3}")
    private double sampleRate;

    @Value("${otel.trace.slow-threshold-ms:1000}")
    private long slowThresholdMs;

    @Value("${otel.trace.batch-size:512}")
    private int batchSize;

    @Value("${otel.trace.schedule-delay-ms:1000}")
    private long scheduleDelayMs;

    @Bean
    public OpenTelemetry openTelemetry() {
        if (!traceEnabled) {
            log.info("Trace is disabled, using noop OpenTelemetry");
            return OpenTelemetry.noop();
        }

        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), serviceName,
                        AttributeKey.stringKey("service.version"), "1.0.0",
                        AttributeKey.stringKey("deployment.environment"),
                        System.getProperty("spring.profiles.active", "default")
                )));

        SpanExporter spanExporter = createSpanExporter();

        Sampler sampler = Sampler.parentBased(Sampler.traceIdRatioBased(sampleRate));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setMaxQueueSize(2048)
                        .setScheduleDelay(scheduleDelayMs, TimeUnit.MILLISECONDS)
                        .setMaxExportBatchSize(batchSize)
                        .setExporterTimeout(30, TimeUnit.SECONDS)
                        .build())
                .setResource(resource)
                .setSampler(sampler)
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OpenTelemetry tracer provider...");
            tracerProvider.close();
        }));

        log.info("OpenTelemetry initialized: serviceName={}, traceEnabled={}, sampleRate={}, slowThresholdMs={}",
                serviceName, traceEnabled, sampleRate, slowThresholdMs);
        return openTelemetry;
    }

    private SpanExporter createSpanExporter() {
        try {
            OtlpGrpcSpanExporter otlpExporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .build();
            log.info("OTLP trace exporter configured: endpoint={}", otlpEndpoint);
            return otlpExporter;
        } catch (Exception e) {
            log.warn("Failed to create OTLP exporter, tracing will be disabled: {}", e.getMessage());
            return SpanExporter.composite();
        }
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        Tracer tracer = openTelemetry.getTracer("agentflow", "1.0.0");
        log.info("Tracer bean created: instrument=agentflow, version=1.0.0");

        AgentTracing.setTracer(tracer);

        return tracer;
    }

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            Tags commonTags = Tags.of(
                    "service", serviceName,
                    "application", "workflow-engine"
            );
            registry.config().commonTags(commonTags);

            AgentMetrics.setMeterRegistry(registry);

            if (metricsEnabled) {
                log.info("MeterRegistry configured with common tags: {}, metrics enabled", commonTags);
            } else {
                log.info("Metrics is disabled");
            }
        };
    }

    @Bean
    public OTelValidator openTelemetryValidator(OpenTelemetry openTelemetry) {
        return new OTelValidator(openTelemetry, traceEnabled);
    }

    @Slf4j
    public static class OTelValidator {
        public OTelValidator(OpenTelemetry openTelemetry, boolean traceEnabled) {
            if (!traceEnabled) {
                log.info("OpenTelemetry validation skipped: trace is disabled");
                return;
            }

            try {
                if (openTelemetry.getTracerProvider() != null) {
                    log.info("OpenTelemetry validation passed: TracerProvider is available");
                }
                if (openTelemetry.getPropagators() != null) {
                    log.info("OpenTelemetry validation passed: ContextPropagators is available");
                }
                log.info("OpenTelemetry initialized successfully");
            } catch (Exception e) {
                log.error("OpenTelemetry validation failed: {}", e.getMessage(), e);
            }
        }
    }
}

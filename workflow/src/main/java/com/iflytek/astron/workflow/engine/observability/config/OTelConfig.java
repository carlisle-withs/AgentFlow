package com.iflytek.astron.workflow.engine.observability.config;

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
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 配置类
 *
 * <p>配置内容：
 * <ul>
 *   <li>TracerProvider - 追踪提供者，支持 OTLP 导出</li>
 *   <li>MeterRegistry - 指标注册器，支持 Prometheus 抓取</li>
 *   <li>Tracer - AgentFlow 专用追踪器</li>
 * </ul>
 */
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

    /**
     * 配置 OpenTelemetry SDK
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        if (!traceEnabled) {
            log.info("Trace is disabled, using noop OpenTelemetry");
            return OpenTelemetry.noop();
        }

        // 创建资源
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        ResourceAttributes.SERVICE_NAME, serviceName,
                        ResourceAttributes.SERVICE_VERSION, "1.0.0"
                )));

        // 创建 Span 导出器（支持 OTLP）
        SpanExporter spanExporter;
        try {
            OtlpGrpcSpanExporter otlpExporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otlpEndpoint)
                    .build();
            spanExporter = otlpExporter;
            log.info("OTLP trace exporter configured: endpoint={}", otlpEndpoint);
        } catch (Exception e) {
            log.warn("Failed to create OTLP exporter, using noop: {}", e.getMessage());
            spanExporter = SpanExporter.noop();
        }

        // 创建追踪提供者
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setResource(resource)
                .setSampler(Sampler.alwaysOn())
                .build();

        // 创建 OpenTelemetry 实例
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));

        log.info("OpenTelemetry initialized: serviceName={}, traceEnabled={}", serviceName, traceEnabled);
        return openTelemetry;
    }

    /**
     * 创建 AgentFlow 专用追踪器
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        Tracer tracer = openTelemetry.getTracer("agentflow", "1.0.0");
        log.info("Tracer bean created: instrument=agentflow, version=1.0.0");

        // 设置到 TraceContext
        TraceContext.setTracer(tracer);

        return tracer;
    }

    /**
     * 配置 Micrometer MeterRegistry
     * 支持 Prometheus 抓取指标
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            Tags commonTags = Tags.of(
                    "service", serviceName,
                    "application", "workflow-engine"
            );
            registry.config().commonTags(commonTags);

            // 设置到 AgentMetrics
            AgentMetrics.setMeterRegistry(registry);

            if (metricsEnabled) {
                log.info("MeterRegistry configured with common tags: {}", commonTags);
            } else {
                log.info("Metrics is disabled");
            }
        };
    }
}

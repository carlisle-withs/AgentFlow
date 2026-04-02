package com.iflytek.astron.workflow.engine.node.impl.agent.model;

import lombok.Data;

import java.util.Map;

/**
 * 执行步骤
 */
@Data
public class Step {

    /**
     * 步骤序号
     */
    private int order;

    /**
     * 步骤描述
     */
    private String description;

    /**
     * 预期工具名称
     */
    private String toolName;

    /**
     * 预估参数
     */
    private Map<String, Object> params;

    /**
     * 步骤目标
     */
    private String goal;

    /**
     * 步骤状态
     */
    private StepStatus status;

    /**
     * 执行结果
     */
    private ExecutionResult result;

    /**
     * 重试次数
     */
    private int retryCount;

    /**
     * 最大重试次数
     */
    private int maxRetries;

    /**
     * 工具执行失败时的错误信息
     */
    private String lastError;

    public Step() {
        this.status = StepStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    public Step(int order, String description) {
        this();
        this.order = order;
        this.description = description;
    }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public enum StepStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }
}

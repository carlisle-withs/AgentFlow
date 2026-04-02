package com.iflytek.astron.workflow.engine.node.impl.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行计划
 */
@Data
public class Plan {

    /**
     * 计划ID
     */
    private String id;

    /**
     * 执行步骤列表
     */
    private List<Step> steps;

    /**
     * 共享上下文
     */
    private Map<String, Object> context;

    /**
     * 当前步骤索引
     */
    private int currentStepIndex;

    /**
     * 计划状态
     */
    private PlanStatus status;

    /**
     * 最终结果
     */
    private Object result;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * 创建时间
     */
    private long createdAt;

    /**
     * 完成时间
     */
    private long completedAt;

    public Plan() {
        this.steps = new ArrayList<>();
        this.context = new HashMap<>();
        this.currentStepIndex = 0;
        this.status = PlanStatus.PLANNING;
        this.createdAt = System.currentTimeMillis();
    }

    public Plan(String id) {
        this();
        this.id = id;
    }

    /**
     * 获取当前步骤
     */
    public Step getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    /**
     * 是否已完成
     */
    public boolean isCompleted() {
        return status == PlanStatus.COMPLETED;
    }

    /**
     * 是否失败
     */
    public boolean isFailed() {
        return status == PlanStatus.FAILED;
    }

    /**
     * 移动到下一步
     */
    public void nextStep() {
        if (currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
        } else {
            this.status = PlanStatus.COMPLETED;
            this.completedAt = System.currentTimeMillis();
        }
    }

    /**
     * 重试当前步骤
     */
    public void retryCurrentStep() {
        Step current = getCurrentStep();
        if (current != null && current.canRetry()) {
            current.incrementRetry();
            current.setStatus(Step.StepStatus.PENDING);
        }
    }

    /**
     * 标记当前步骤失败
     */
    public void failCurrentStep(String reason) {
        Step current = getCurrentStep();
        if (current != null) {
            current.setStatus(Step.StepStatus.FAILED);
            current.setLastError(reason);
        }
        this.failureReason = reason;
    }

    /**
     * 添加步骤
     */
    public void addStep(Step step) {
        this.steps.add(step);
    }

    /**
     * 获取已完成步骤数
     */
    public int getCompletedStepCount() {
        return (int) steps.stream()
                .filter(s -> s.getStatus() == Step.StepStatus.COMPLETED)
                .count();
    }

    /**
     * 获取总步骤数
     */
    public int getTotalStepCount() {
        return steps.size();
    }

    /**
     * 计算进度百分比
     */
    public double getProgress() {
        if (steps.isEmpty()) return 0.0;
        return (double) getCompletedStepCount() / steps.size() * 100;
    }

    public enum PlanStatus {
        PLANNING,      // 规划中
        EXECUTING,     // 执行中
        COMPLETED,     // 已完成
        FAILED,        // 失败
        CANCELLED      // 取消
    }
}

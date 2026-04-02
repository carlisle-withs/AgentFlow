package com.iflytek.astron.workflow.engine.node.impl.agent.impl;

import com.iflytek.astron.workflow.engine.node.impl.agent.core.ReAct;
import com.iflytek.astron.workflow.engine.node.impl.agent.core.Reflect;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.ExecutionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Plan;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.ReflectionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Step;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认反思器实现
 * 负责评估执行结果，决定下一步行动
 */
@Slf4j
public class DefaultReflect implements Reflect {

    private final ReflectConfig config;
    private final LoopDetector loopDetector;

    public DefaultReflect() {
        this.config = new ReflectConfig();
        this.loopDetector = new LoopDetector();
    }

    public DefaultReflect(ReflectConfig config) {
        this.config = config;
        this.loopDetector = new LoopDetector(config.getLoopThreshold());
    }

    @Override
    public ReflectionResult evaluateStep(ExecutionResult result, Step step) {
        // 检查执行结果
        if (result.isSuccess()) {
            return ReflectionResult.continue_("Step executed successfully");
        }

        // 执行失败，分析原因
        String error = result.getError();
        if (error == null) {
            error = "Unknown error";
        }

        // 判断错误类型
        if (isRetryableError(error)) {
            // 可重试错误
            if (step.canRetry()) {
                return ReflectionResult.retry("Retrying step due to: " + error);
            } else {
                return ReflectionResult.terminate(
                        "Step failed after max retries: " + step.getDescription() + ". Error: " + error);
            }
        }

        // 不可重试错误
        return ReflectionResult.terminate(
                "Non-retryable error in step: " + step.getDescription() + ". Error: " + error);
    }

    @Override
    public boolean needRevise(Plan plan) {
        // 检查是否需要修正计划
        if (plan.isFailed()) {
            return true;
        }

        // 检查连续失败
        long failedSteps = plan.getSteps().stream()
                .filter(s -> s.getStatus() == Step.StepStatus.FAILED)
                .count();

        return failedSteps > config.getMaxConsecutiveFailures();
    }

    @Override
    public String shouldTerminate(Plan plan) {
        // 检查是否完成
        if (plan.isCompleted()) {
            return null; // 不终止
        }

        // 检查是否失败
        if (plan.isFailed()) {
            return plan.getFailureReason();
        }

        // 检查是否达到最大步骤数
        if (plan.getCurrentStepIndex() >= config.getMaxSteps()) {
            return "Reached maximum steps limit";
        }

        // 检查死循环
        if (loopDetector.isLooping(plan.getSteps())) {
            return "Detected loop in execution";
        }

        return null; // 不终止
    }

    @Override
    public boolean detectLoop(List<ExecutionResult> recentSteps) {
        return loopDetector.isLooping(recentSteps);
    }

    @Override
    public String getReflectionSummary(ExecutionResult result) {
        StringBuilder sb = new StringBuilder();

        if (result.isSuccess()) {
            sb.append("Success: ");
            if (result.getType() == ExecutionResult.ExecutionType.TOOL_CALL) {
                sb.append("Tool '").append(result.getToolName()).append("' executed");
            } else {
                sb.append("Reasoning completed");
            }
        } else {
            sb.append("Failure: ").append(result.getError());
        }

        return sb.toString();
    }

    /**
     * 判断错误是否可重试
     */
    private boolean isRetryableError(String error) {
        if (error == null) return false;

        // 网络错误、临时错误可重试
        String[] retryablePatterns = {
                "timeout", "TIMEOUT",
                "connection", "CONNECTION",
                "network", "NETWORK",
                "temporarily", "TEMPORARILY",
                "rate limit", "RATE LIMIT",
                "service unavailable", "SERVICE UNAVAILABLE"
        };

        String lowerError = error.toLowerCase();
        for (String pattern : retryablePatterns) {
            if (lowerError.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    @Data
    public static class ReflectConfig {
        /**
         * 最大执行步骤数
         */
        private int maxSteps = 20;

        /**
         * 最大连续失败次数
         */
        private int maxConsecutiveFailures = 3;

        /**
         * 死循环检测阈值
         */
        private int loopThreshold = 3;

        /**
         * 相似度阈值
         */
        private double similarityThreshold = 0.8;
    }

    /**
     * 死循环检测器
     */
    @Data
    public static class LoopDetector {

        private final int threshold;
        private final List<String> recentOutputs;
        private final Map<String, Integer> outputCounts;

        public LoopDetector() {
            this(3);
        }

        public LoopDetector(int threshold) {
            this.threshold = threshold;
            this.recentOutputs = new ArrayList<>();
            this.outputCounts = new HashMap<>();
        }

        /**
         * 检测是否陷入循环
         */
        public boolean isLooping(List<Step> steps) {
            if (steps.isEmpty()) return false;

            // 获取最近几个步骤的输出
            List<String> recent = new ArrayList<>();
            int count = Math.min(steps.size(), threshold);
            for (int i = steps.size() - count; i < steps.size(); i++) {
                Step step = steps.get(i);
                if (step.getResult() != null) {
                    recent.add(step.getResult().getOutput());
                }
            }

            // 检查是否有重复输出
            for (String output : recent) {
                int freq = outputCounts.getOrDefault(output, 0) + 1;
                outputCounts.put(output, freq);
                if (freq >= threshold) {
                    log.warn("Detected potential loop: output '{}' repeated {} times", output, freq);
                    return true;
                }
            }

            return false;
        }

        /**
         * 检测执行结果是否循环
         */
        public boolean isLooping(List<ExecutionResult> recentSteps) {
            if (recentSteps.size() < threshold) {
                return false;
            }

            List<String> outputs = new ArrayList<>();
            for (ExecutionResult result : recentSteps) {
                outputs.add(result.getOutput());
            }

            // 检查最后几个输出是否相同
            String lastOutput = outputs.get(outputs.size() - 1);
            int repeatCount = 1;

            for (int i = outputs.size() - 2; i >= 0; i--) {
                if (outputs.get(i).equals(lastOutput)) {
                    repeatCount++;
                } else {
                    break;
                }
            }

            return repeatCount >= threshold;
        }

        /**
         * 重置检测器
         */
        public void reset() {
            recentOutputs.clear();
            outputCounts.clear();
        }
    }
}

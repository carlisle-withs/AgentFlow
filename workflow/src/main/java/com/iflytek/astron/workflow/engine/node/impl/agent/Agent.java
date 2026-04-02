package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.iflytek.astron.workflow.engine.node.impl.agent.core.Memory;
import com.iflytek.astron.workflow.engine.node.impl.agent.core.Planner;
import com.iflytek.astron.workflow.engine.node.impl.agent.core.ReAct;
import com.iflytek.astron.workflow.engine.node.impl.agent.core.Reflect;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultMemory;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultPlanner;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultReflect;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultReAct;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.ExecutionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.MemoryItem;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Plan;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.ReflectionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Step;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Task;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 主入口
 * 整合 Planner、ReAct、Reflect、Memory 实现完整的自主推理Agent
 */
@Slf4j
public class Agent {

    private final Planner planner;
    private final ReAct react;
    private final Reflect reflect;
    private final Memory memory;

    private final AgentConfig config;

    /**
     * 当前计划
     */
    private Plan currentPlan;

    /**
     * 执行上下文
     */
    private Map<String, Object> executionContext;

    /**
     * 私有构造函数，强制使用组件注入
     */
    public Agent(Planner planner, ReAct react, Reflect reflect, Memory memory, AgentConfig config) {
        this.planner = planner;
        this.react = react;
        this.reflect = reflect;
        this.memory = memory;
        this.config = config != null ? config : new AgentConfig();
        this.executionContext = new HashMap<>();
    }

    /**
     * 静态工厂方法 - 创建带默认实现的 Agent
     * 注意：ReAct 需要外部注入，否则执行会失败
     */
    public static Agent createWithDefaults(AgentConfig config) {
        Agent agent = new Agent(
                new DefaultPlanner(),
                null, // ReAct 需要外部注入
                new DefaultReflect(),
                new DefaultMemory(),
                config
        );
        return agent;
    }

    /**
     * 执行任务
     *
     * @param task           任务
     * @param availableTools 可用工具
     * @return 执行结果
     */
    public AgentOutput execute(Task task, List<String> availableTools) {
        log.info("Agent executing task: {}", task.getDescription());

        // 1. 清空上次的记忆
        memory.clear();

        // 2. Planner 生成初始计划
        currentPlan = planner.generatePlan(task, availableTools);
        if (currentPlan.isFailed()) {
            return AgentOutput.failure("Failed to generate plan: " + currentPlan.getFailureReason());
        }

        // 3. 主循环
        while (!currentPlan.isCompleted() && !currentPlan.isFailed()) {
            // 检查是否达到终止条件
            String terminationReason = reflect.shouldTerminate(currentPlan);
            if (terminationReason != null) {
                log.warn("Agent terminated: {}", terminationReason);
                currentPlan.setStatus(Plan.PlanStatus.FAILED);
                currentPlan.setFailureReason(terminationReason);
                break;
            }

            // 获取当前步骤
            Step currentStep = currentPlan.getCurrentStep();
            if (currentStep == null) {
                // 没有更多步骤，完成
                currentPlan.setStatus(Plan.PlanStatus.COMPLETED);
                break;
            }

            // 标记步骤开始
            currentStep.setStatus(Step.StepStatus.RUNNING);

            // 4. ReAct 执行当前步骤
            ExecutionResult result = executeStepWithContext(currentStep);

            // 5. Memory 记录
            memory.addResult(result);

            // 6. Reflect 评估执行结果
            ReflectionResult reflection = reflect.evaluateStep(result, currentStep);

            // 处理反思结果
            switch (reflection.getNextAction()) {
                case CONTINUE:
                    // 步骤成功，继续下一步
                    currentStep.setStatus(Step.StepStatus.COMPLETED);
                    currentStep.setResult(result);
                    currentPlan.nextStep();
                    break;

                case RETRY:
                    // 需要重试
                    log.info("Retrying step: {}", currentStep.getDescription());
                    currentPlan.retryCurrentStep();
                    break;

                case REVISE:
                    // 需要修正计划
                    log.info("Revising plan");
                    currentPlan = planner.revisePlan(currentPlan, currentStep, reflection.getReasoning());
                    if (currentPlan.isFailed()) {
                        currentPlan.setFailureReason(reflection.getReasoning());
                    }
                    break;

                case TERMINATE:
                    // 终止
                    log.warn("Agent terminating: {}", reflection.getReasoning());
                    currentPlan.setStatus(Plan.PlanStatus.FAILED);
                    currentPlan.setFailureReason(reflection.getReasoning());
                    currentStep.setStatus(Step.StepStatus.FAILED);
                    currentStep.setLastError(reflection.getReasoning());
                    break;

                case COMPLETED:
                    // 计划完成
                    currentPlan.setStatus(Plan.PlanStatus.COMPLETED);
                    currentPlan.setResult(result.getOutput());
                    break;
            }

            // 检查是否需要压缩记忆
            if (memory.needsCompression(config.getMaxContextTokens())) {
                memory.compress(config.getMaxContextTokens());
            }
        }

        // 7. 构建输出
        return buildOutput();
    }

    /**
     * 执行步骤（带上下文）
     */
    private ExecutionResult executeStepWithContext(Step step) {
        ReAct.ExecutionContext context = new ReAct.ExecutionContext() {
            @Override
            public String getSystemPrompt() {
                return config.getSystemPrompt();
            }

            @Override
            public int getCurrentStep() {
                return step.getOrder();
            }

            @Override
            public String getMemoryContext() {
                return memory.getCompressedContext(config.getMaxContextTokens());
            }

            @Override
            public Object getInput(String key) {
                return executionContext.get(key);
            }

            @Override
            public Map<String, Object> getInputs() {
                return new HashMap<>(executionContext);
            }

            @Override
            public void addOutput(String key, Object value) {
                executionContext.put(key, value);
            }
        };

        return react.executeStep(step, context);
    }

    /**
     * 构建输出
     */
    private AgentOutput buildOutput() {
        AgentOutput output = new AgentOutput();
        output.setSuccess(currentPlan.isCompleted());
        output.setPlanId(currentPlan.getId());
        output.setTotalSteps(currentPlan.getTotalStepCount());
        output.setCompletedSteps(currentPlan.getCompletedStepCount());
        output.setProgress(currentPlan.getProgress());
        output.setMemorySize(memory.size());

        if (currentPlan.isCompleted()) {
            output.setResult(currentPlan.getResult());
            output.setMessage("Task completed successfully");
        } else {
            output.setError(currentPlan.getFailureReason());
            output.setMessage("Task failed: " + currentPlan.getFailureReason());
        }

        // 添加执行历史摘要
        List<MemoryItem> keyMemories = memory.getKeyMemories();
        if (!keyMemories.isEmpty()) {
            output.setKeyMemories(keyMemories);
        }

        return output;
    }

    /**
     * 添加上下文输入
     */
    public void addContext(String key, Object value) {
        this.executionContext.put(key, value);
    }

    /**
     * 获取当前计划
     */
    public Plan getCurrentPlan() {
        return currentPlan;
    }

    /**
     * 获取记忆
     */
    public Memory getMemory() {
        return memory;
    }

    /**
     * 配置类
     */
    @Data
    public static class AgentConfig {
        /**
         * 系统提示
         */
        private String systemPrompt = "你是一个智能助手，负责完成用户任务。";

        /**
         * 最大上下文 token 数
         */
        private int maxContextTokens = 8000;

        /**
         * 最大执行步骤
         */
        private int maxSteps = 20;

        /**
         * 是否启用反思
         */
        private boolean enableReflect = true;

        /**
         * 是否启用记忆压缩
         */
        private boolean enableMemoryCompression = true;
    }

    /**
     * Agent 输出
     */
    @Data
    public static class AgentOutput {
        /**
         * 是否成功
         */
        private boolean success;

        /**
         * 输出结果
         */
        private Object result;

        /**
         * 错误信息
         */
        private String error;

        /**
         * 消息
         */
        private String message;

        /**
         * 计划ID
         */
        private String planId;

        /**
         * 总步骤数
         */
        private int totalSteps;

        /**
         * 完成步骤数
         */
        private int completedSteps;

        /**
         * 进度百分比
         */
        private double progress;

        /**
         * 记忆大小
         */
        private int memorySize;

        /**
         * 关键记忆
         */
        private List<MemoryItem> keyMemories;

        public static AgentOutput failure(String error) {
            AgentOutput output = new AgentOutput();
            output.setSuccess(false);
            output.setError(error);
            output.setMessage("Task failed");
            return output;
        }
    }
}

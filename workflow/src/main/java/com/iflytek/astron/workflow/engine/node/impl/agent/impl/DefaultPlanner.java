package com.iflytek.astron.workflow.engine.node.impl.agent.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.workflow.engine.node.impl.agent.core.Planner;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Plan;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Step;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Task;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认规划器实现
 * 使用 LLM 进行任务拆解
 */
@Slf4j
public class DefaultPlanner implements Planner {

    private final PlannerConfig config;

    public DefaultPlanner() {
        this.config = new PlannerConfig();
    }

    public DefaultPlanner(PlannerConfig config) {
        this.config = config;
    }

    @Override
    public Plan generatePlan(Task task, List<String> availableTools) {
        Plan plan = new Plan(UUID.randomUUID().toString());

        try {
            // 构建规划提示
            String planningPrompt = buildPlanningPrompt(task, availableTools);

            // 调用 LLM 生成计划
            String llmResponse = callLLMForPlanning(planningPrompt);

            // 解析计划
            List<Step> steps = parsePlanSteps(llmResponse);

            if (steps.isEmpty()) {
                // 如果解析失败，生成默认计划（单个步骤）
                log.warn("Failed to parse plan from LLM response, using single step plan");
                Step defaultStep = new Step(1, task.getDescription());
                defaultStep.setGoal(task.getGoal());
                steps.add(defaultStep);
            }

            plan.getSteps().addAll(steps);
            plan.setStatus(Plan.PlanStatus.EXECUTING);

            log.info("Generated plan with {} steps for task: {}", steps.size(), task.getDescription());

        } catch (Exception e) {
            log.error("Failed to generate plan: {}", e.getMessage(), e);
            plan.setStatus(Plan.PlanStatus.FAILED);
            plan.setFailureReason("Failed to generate plan: " + e.getMessage());
        }

        return plan;
    }

    @Override
    public Plan revisePlan(Plan currentPlan, Step failedStep, String error) {
        log.info("Revising plan due to step failure: {}", failedStep.getDescription());

        // 构建修正提示
        String revisePrompt = buildRevisePrompt(currentPlan, failedStep, error);

        try {
            String llmResponse = callLLMForPlanning(revisePrompt);
            List<Step> newSteps = parsePlanSteps(llmResponse);

            if (!newSteps.isEmpty()) {
                // 创建新计划，保留已完成步骤
                Plan revisedPlan = new Plan(UUID.randomUUID().toString());
                revisedPlan.getSteps().addAll(newSteps);
                revisedPlan.setStatus(Plan.PlanStatus.EXECUTING);
                return revisedPlan;
            }

        } catch (Exception e) {
            log.error("Failed to revise plan: {}", e.getMessage(), e);
        }

        // 修正失败，返回原计划标记为失败
        currentPlan.setStatus(Plan.PlanStatus.FAILED);
        currentPlan.setFailureReason(error);
        return currentPlan;
    }

    @Override
    public boolean assessPlanViability(Plan plan) {
        if (plan.getSteps().isEmpty()) {
            return false;
        }

        // 检查是否有未完成的步骤
        long pendingSteps = plan.getSteps().stream()
                .filter(s -> s.getStatus() == Step.StepStatus.PENDING)
                .count();

        return pendingSteps > 0 && !plan.isFailed();
    }

    @Override
    public String getPlanSummary(Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Plan [").append(plan.getId()).append("]\n");
        sb.append("Status: ").append(plan.getStatus()).append("\n");
        sb.append("Progress: ").append(plan.getCompletedStepCount())
                .append("/").append(plan.getTotalStepCount()).append("\n");

        for (Step step : plan.getSteps()) {
            sb.append(String.format("  %d. [%s] %s",
                    step.getOrder(),
                    step.getStatus(),
                    step.getDescription()));
            if (step.getToolName() != null) {
                sb.append(" (tool: ").append(step.getToolName()).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建规划提示
     */
    private String buildPlanningPrompt(Task task, List<String> availableTools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个任务规划专家。请将以下任务拆解成具体的执行步骤。\n\n");
        prompt.append("## 任务\n").append(task.getDescription()).append("\n\n");

        if (task.getGoal() != null) {
            prompt.append("## 目标\n").append(task.getGoal()).append("\n\n");
        }

        prompt.append("## 可用工具\n");
        for (String tool : availableTools) {
            prompt.append("- ").append(tool).append("\n");
        }
        prompt.append("\n");

        prompt.append("## 要求\n");
        prompt.append("1. 将任务拆解成 2-5 个具体步骤\n");
        prompt.append("2. 每个步骤应该可以独立执行或依赖前序步骤的结果\n");
        prompt.append("3. 每个步骤需指定预期使用的工具（如果有）\n");
        prompt.append("4. 输出格式为 JSON 数组\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("```json\n");
        prompt.append("[\n");
        prompt.append("  {\"order\": 1, \"description\": \"步骤1描述\", \"toolName\": \"工具名\", \"goal\": \"步骤目标\"},\n");
        prompt.append("  {\"order\": 2, \"description\": \"步骤2描述\", \"toolName\": \"工具名\", \"goal\": \"步骤目标\"}\n");
        prompt.append("]\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    /**
     * 构建修正提示
     */
    private String buildRevisePrompt(Plan currentPlan, Step failedStep, String error) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("任务执行过程中遇到问题，需要修正计划。\n\n");

        prompt.append("## 失败步骤\n");
        prompt.append("步骤: ").append(failedStep.getDescription()).append("\n");
        prompt.append("错误: ").append(error).append("\n\n");

        prompt.append("## 当前计划\n");
        prompt.append(getPlanSummary(currentPlan)).append("\n");

        prompt.append("## 要求\n");
        prompt.append("1. 分析失败原因\n");
        prompt.append("2. 提出修正方案（可以跳过、重试、替换工具或调整步骤）\n");
        prompt.append("3. 输出修正后的计划（JSON 数组格式）\n\n");

        prompt.append("## 输出格式\n");
        prompt.append("```json\n");
        prompt.append("[\n");
        prompt.append("  {\"order\": 1, \"description\": \"步骤描述\", \"toolName\": \"工具名\", \"goal\": \"步骤目标\"}\n");
        prompt.append("]\n");
        prompt.append("```\n");

        return prompt.toString();
    }

    /**
     * 调用 LLM 生成计划（这里需要注入 ModelServiceClient）
     */
    protected String callLLMForPlanning(String prompt) {
        // TODO: 实际调用需要注入 ModelServiceClient
        // 目前返回空实现，由子类或外部注入实现
        throw new UnsupportedOperationException(
                "callLLMForPlanning must be implemented or ModelServiceClient must be injected");
    }

    /**
     * 解析计划步骤
     */
    protected List<Step> parsePlanSteps(String llmResponse) {
        List<Step> steps = new ArrayList<>();

        try {
            // 提取 JSON 部分
            String jsonStr = extractJson(llmResponse);
            if (jsonStr == null) {
                return steps;
            }

            JSONArray jsonArray = JSON.parseArray(jsonStr);
            if (jsonArray == null) {
                return steps;
            }

            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject stepObj = jsonArray.getJSONObject(i);
                Step step = new Step();
                step.setOrder(stepObj.getIntValue("order", i + 1));
                step.setDescription(stepObj.getString("description"));
                step.setToolName(stepObj.getString("toolName"));
                step.setGoal(stepObj.getString("goal"));

                // 解析预估参数
                if (stepObj.containsKey("params")) {
                    step.setParams(stepObj.getJSONObject("params"));
                }

                steps.add(step);
            }

        } catch (Exception e) {
            log.error("Failed to parse plan steps: {}", e.getMessage(), e);
        }

        return steps;
    }

    /**
     * 从 LLM 响应中提取 JSON
     */
    private String extractJson(String response) {
        if (response == null) return null;

        // 尝试找 JSON 数组
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }

        // 尝试找 JSON 对象
        start = response.indexOf('{');
        end = response.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            return "[" + response.substring(start, end + 1) + "]";
        }

        return null;
    }

    @Data
    public static class PlannerConfig {
        /**
         * 最大规划步骤数
         */
        private int maxPlanSteps = 10;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 是否启用 LLM 规划
         */
        private boolean enableLlmPlanning = true;
    }
}

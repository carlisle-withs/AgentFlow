package com.iflytek.astron.workflow.engine.node.impl.agent.core;

import com.iflytek.astron.workflow.engine.node.impl.agent.model.Plan;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Step;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Task;

import java.util.List;

/**
 * 规划器接口
 * 负责将复杂任务拆解为可执行的步骤计划
 */
public interface Planner {

    /**
     * 生成初始计划
     *
     * @param task            任务
     * @param availableTools  可用工具列表
     * @return 执行计划
     */
    Plan generatePlan(Task task, List<String> availableTools);

    /**
     * 修正计划（执行中发现问题）
     *
     * @param currentPlan  当前计划
     * @param failedStep   失败的步骤
     * @param error        错误信息
     * @return 修正后的计划
     */
    Plan revisePlan(Plan currentPlan, Step failedStep, String error);

    /**
     * 评估计划可行性
     *
     * @param plan  计划
     * @return 是否可行
     */
    boolean assessPlanViability(Plan plan);

    /**
     * 获取计划摘要
     *
     * @param plan  计划
     * @return 摘要信息
     */
    String getPlanSummary(Plan plan);
}

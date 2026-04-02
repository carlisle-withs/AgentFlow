package com.iflytek.astron.workflow.engine.node.impl.agent.core;

import com.iflytek.astron.workflow.engine.node.impl.agent.model.ExecutionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Plan;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.ReflectionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Step;

/**
 * 反思器接口
 * 负责评估执行结果，决定是否继续/修正/终止
 */
public interface Reflect {

    /**
     * 评估步骤执行结果
     *
     * @param result  执行结果
     * @param step    当前步骤
     * @return 反思结果
     */
    ReflectionResult evaluateStep(ExecutionResult result, Step step);

    /**
     * 评估计划是否仍可行
     *
     * @param plan  当前计划
     * @return 是否可行
     */
    boolean needRevise(Plan plan);

    /**
     * 评估是否达到终止条件
     *
     * @param plan  当前计划
     * @return 终止原因，null表示不终止
     */
    String shouldTerminate(Plan plan);

    /**
     * 检测是否陷入死循环
     *
     * @param recentSteps 最近执行的步骤
     * @return 是否死循环
     */
    boolean detectLoop(java.util.List<ExecutionResult> recentSteps);

    /**
     * 获取反思摘要
     *
     * @param result  执行结果
     * @return 摘要信息
     */
    String getReflectionSummary(ExecutionResult result);
}

package com.iflytek.astron.workflow.engine.node.impl.agent.model;

import lombok.Data;

/**
 * 反思结果
 */
@Data
public class ReflectionResult {

    /**
     * 步骤是否成功
     */
    private boolean success;

    /**
     * 整体计划是否继续可行
     */
    private boolean planViable;

    /**
     * 下一步行动
     */
    private NextAction nextAction;

    /**
     * 反思理由
     */
    private String reasoning;

    /**
     * 修正后的计划（如需）
     */
    private Plan revisedPlan;

    /**
     * 建议的替代工具
     */
    private String suggestedAlternativeTool;

    /**
     * 创建时间
     */
    private long timestamp;

    public ReflectionResult() {
        this.timestamp = System.currentTimeMillis();
        this.planViable = true;
    }

    /**
     * 继续执行
     */
    public static ReflectionResult continue_(String reasoning) {
        ReflectionResult result = new ReflectionResult();
        result.setSuccess(true);
        result.setPlanViable(true);
        result.setNextAction(NextAction.CONTINUE);
        result.setReasoning(reasoning);
        return result;
    }

    /**
     * 需要修正计划
     */
    public static ReflectionResult revise(String reasoning, Plan revisedPlan) {
        ReflectionResult result = new ReflectionResult();
        result.setSuccess(false);
        result.setPlanViable(true);
        result.setNextAction(NextAction.REVISE);
        result.setReasoning(reasoning);
        result.setRevisedPlan(revisedPlan);
        return result;
    }

    /**
     * 重试当前步骤
     */
    public static ReflectionResult retry(String reasoning) {
        ReflectionResult result = new ReflectionResult();
        result.setSuccess(false);
        result.setPlanViable(true);
        result.setNextAction(NextAction.RETRY);
        result.setReasoning(reasoning);
        return result;
    }

    /**
     * 终止并汇报
     */
    public static ReflectionResult terminate(String reasoning) {
        ReflectionResult result = new ReflectionResult();
        result.setSuccess(false);
        result.setPlanViable(false);
        result.setNextAction(NextAction.TERMINATE);
        result.setReasoning(reasoning);
        return result;
    }

    /**
     * 计划执行完毕
     */
    public static ReflectionResult completed(String reasoning) {
        ReflectionResult result = new ReflectionResult();
        result.setSuccess(true);
        result.setPlanViable(true);
        result.setNextAction(NextAction.COMPLETED);
        result.setReasoning(reasoning);
        return result;
    }

    public enum NextAction {
        CONTINUE,   // 继续下一步
        REVISE,     // 修正计划
        RETRY,      // 重试本步骤
        TERMINATE,  // 终止
        COMPLETED   // 完成
    }
}

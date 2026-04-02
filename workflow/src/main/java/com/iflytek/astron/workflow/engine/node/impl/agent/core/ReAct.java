package com.iflytek.astron.workflow.engine.node.impl.agent.core;

import com.iflytek.astron.workflow.engine.node.impl.agent.model.ExecutionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Step;

/**
 * ReAct 执行器接口
 * 负责单步推理和工具调用
 */
public interface ReAct {

    /**
     * 执行单个步骤
     *
     * @param step     步骤
     * @param context  执行上下文
     * @return 执行结果
     */
    ExecutionResult executeStep(Step step, ExecutionContext context);

    /**
     * 推理（不调用工具）
     *
     * @param prompt   推理提示
     * @param context  执行上下文
     * @return 推理结果
     */
    ExecutionResult reasoning(String prompt, ExecutionContext context);

    /**
     * 调用工具
     *
     * @param toolName 工具名称
     * @param args     工具参数
     * @param context  执行上下文
     * @return 工具执行结果
     */
    ExecutionResult callTool(String toolName, Object args, ExecutionContext context);

    /**
     * 执行上下文
     */
    interface ExecutionContext {
        /**
         * 获取系统提示
         */
        String getSystemPrompt();

        /**
         * 获取当前步骤序号
         */
        int getCurrentStep();

        /**
         * 获取记忆内容
         */
        String getMemoryContext();

        /**
         * 获取输入参数
         */
        Object getInput(String key);

        /**
         * 获取所有输入
         */
        java.util.Map<String, Object> getInputs();

        /**
         * 添加输出
         */
        void addOutput(String key, Object value);
    }
}

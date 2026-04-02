package com.iflytek.astron.workflow.engine.node.impl.agent.model;

import lombok.Data;

import java.util.Map;

/**
 * 执行结果
 */
@Data
public class ExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 执行类型
     */
    private ExecutionType type;

    /**
     * 输出内容
     */
    private String output;

    /**
     * 工具调用结果
     */
    private Map<String, Object> toolResult;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 工具名称（如果是工具调用）
     */
    private String toolName;

    /**
     * 推理过程
     */
    private String reasoning;

    /**
     * 执行耗时（毫秒）
     */
    private long executionTime;

    /**
     * 执行步骤
     */
    private int step;

    /**
     * 创建时间
     */
    private long timestamp;

    public ExecutionResult() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 创建成功结果
     */
    public static ExecutionResult success(String output) {
        ExecutionResult result = new ExecutionResult();
        result.setSuccess(true);
        result.setOutput(output);
        return result;
    }

    /**
     * 创建工具调用结果
     */
    public static ExecutionResult toolResult(String toolName, Map<String, Object> toolResult) {
        ExecutionResult result = new ExecutionResult();
        result.setSuccess(true);
        result.setType(ExecutionType.TOOL_CALL);
        result.setToolName(toolName);
        result.setToolResult(toolResult);
        return result;
    }

    /**
     * 创建失败结果
     */
    public static ExecutionResult failure(String error) {
        ExecutionResult result = new ExecutionResult();
        result.setSuccess(false);
        result.setError(error);
        return result;
    }

    /**
     * 转换为记忆项
     */
    public MemoryItem toMemoryItem() {
        MemoryItem item = new MemoryItem();
        item.setStep(this.step);
        item.setTimestamp(this.timestamp);

        if (type == ExecutionType.TOOL_CALL) {
            item.setType("tool_call");
            item.setContent(String.format("%s(%s) -> %s",
                    toolName,
                    toolResult != null ? toolResult.toString() : "",
                    output));
        } else if (success) {
            item.setType("result");
            item.setContent(output);
        } else {
            item.setType("error");
            item.setContent(error);
        }

        return item;
    }

    public enum ExecutionType {
        REASONING,     // 推理
        TOOL_CALL,     // 工具调用
        FINAL_ANSWER,  // 最终答案
        ERROR          // 错误
    }
}

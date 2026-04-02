package com.iflytek.astron.workflow.engine.node.impl.agent.impl;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.workflow.engine.node.impl.agent.core.ReAct;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.ExecutionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Step;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认 ReAct 执行器实现
 */
@Slf4j
public class DefaultReAct implements ReAct {

    private final ModelClient modelClient;
    private final ToolExecutor toolExecutor;
    private final ReActConfig config;

    public DefaultReAct(ModelClient modelClient, ToolExecutor toolExecutor) {
        this.modelClient = modelClient;
        this.toolExecutor = toolExecutor;
        this.config = new ReActConfig();
    }

    public DefaultReAct(ModelClient modelClient, ToolExecutor toolExecutor, ReActConfig config) {
        this.modelClient = modelClient;
        this.toolExecutor = toolExecutor;
        this.config = config;
    }

    @Override
    public ExecutionResult executeStep(Step step, ExecutionContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // 构建执行提示
            String prompt = buildStepPrompt(step, context);

            // 调用 LLM
            String llmOutput = modelClient.chat(prompt, context);

            // 解析输出
            Action action = parseAction(llmOutput);

            // 执行动作
            return executeAction(action, context, startTime, step.getOrder());

        } catch (Exception e) {
            log.error("Step execution failed: {}", e.getMessage(), e);
            return ExecutionResult.failure(e.getMessage());
        }
    }

    @Override
    public ExecutionResult reasoning(String prompt, ExecutionContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String llmOutput = modelClient.chat(prompt, context);

            ExecutionResult result = new ExecutionResult();
            result.setSuccess(true);
            result.setType(ExecutionResult.ExecutionType.REASONING);
            result.setOutput(llmOutput);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            return result;

        } catch (Exception e) {
            log.error("Reasoning failed: {}", e.getMessage(), e);
            return ExecutionResult.failure(e.getMessage());
        }
    }

    @Override
    public ExecutionResult callTool(String toolName, Object args, ExecutionContext context) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> toolResult = toolExecutor.execute(toolName, args, context);

            ExecutionResult result = ExecutionResult.toolResult(toolName, toolResult);
            result.setExecutionTime(System.currentTimeMillis() - startTime);

            return result;

        } catch (Exception e) {
            log.error("Tool call failed: {} - {}", toolName, e.getMessage(), e);
            ExecutionResult result = ExecutionResult.failure(e.getMessage());
            result.setType(ExecutionResult.ExecutionType.TOOL_CALL);
            result.setToolName(toolName);
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 构建步骤执行提示
     */
    private String buildStepPrompt(Step step, ExecutionContext context) {
        StringBuilder prompt = new StringBuilder();

        // 系统提示
        prompt.append(context.getSystemPrompt()).append("\n\n");

        // 当前步骤
        prompt.append("## 当前步骤\n").append(step.getDescription()).append("\n\n");

        // 步骤目标
        if (step.getGoal() != null) {
            prompt.append("## 目标\n").append(step.getGoal()).append("\n\n");
        }

        // 记忆上下文
        String memoryContext = context.getMemoryContext();
        if (memoryContext != null && !memoryContext.isEmpty()) {
            prompt.append("## 执行历史\n").append(memoryContext).append("\n\n");
        }

        // 输入参数
        Map<String, Object> inputs = context.getInputs();
        if (inputs != null && !inputs.isEmpty()) {
            prompt.append("## 当前输入\n");
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                prompt.append(String.format("- %s: %s\n", entry.getKey(), formatValue(entry.getValue())));
            }
            prompt.append("\n");
        }

        // 输出格式说明
        prompt.append("## 输出格式\n");
        prompt.append("请按照以下格式输出：\n\n");
        prompt.append("1. 如果需要工具辅助完成任务：\n");
        prompt.append("<thinking>你的推理过程...</thinking>\n");
        prompt.append("<tool_call>{\"name\": \"工具名称\", \"arguments\": {\"参数\": \"值\"}}</tool_call>\n\n");
        prompt.append("2. 如果任务已完成：\n");
        prompt.append("<thinking>你的推理过程...</thinking>\n");
        prompt.append("<final_answer>最终答案内容</final_answer>\n");

        return prompt.toString();
    }

    /**
     * 解析 LLM 输出
     */
    private Action parseAction(String llmOutput) {
        Action action = new Action();

        if (llmOutput == null || llmOutput.isEmpty()) {
            action.type = Action.Type.ERROR;
            action.error = "Empty LLM output";
            return action;
        }

        // 提取 thinking
        action.thinking = extractTag(llmOutput, "thinking");

        // 检查 final_answer
        if (llmOutput.contains("<final_answer>")) {
            action.type = Action.Type.FINAL_ANSWER;
            action.content = extractFinalAnswer(llmOutput);
            return action;
        }

        // 检查 tool_call
        if (llmOutput.contains("<tool_call>")) {
            action.type = Action.Type.TOOL_CALL;
            action.toolName = extractToolName(llmOutput);
            action.toolArgs = extractToolArgs(llmOutput);
            return action;
        }

        // 默认视为推理继续
        action.type = Action.Type.REASONING;
        action.content = llmOutput;
        return action;
    }

    /**
     * 执行解析后的动作
     */
    private ExecutionResult executeAction(Action action, ExecutionContext context,
                                          long startTime, int stepOrder) {
        ExecutionResult result = new ExecutionResult();
        result.setStep(stepOrder);
        result.setExecutionTime(System.currentTimeMillis() - startTime);

        switch (action.type) {
            case FINAL_ANSWER:
                result.setSuccess(true);
                result.setType(ExecutionResult.ExecutionType.FINAL_ANSWER);
                result.setOutput(action.content);
                result.setReasoning(action.thinking);
                break;

            case TOOL_CALL:
                // 执行工具调用
                ExecutionResult toolResult = callTool(action.toolName, action.toolArgs, context);
                result.setSuccess(toolResult.isSuccess());
                result.setType(ExecutionResult.ExecutionType.TOOL_CALL);
                result.setToolName(action.toolName);
                result.setToolResult(toolResult.getToolResult());
                result.setError(toolResult.getError());
                result.setReasoning(action.thinking);
                break;

            case REASONING:
                result.setSuccess(true);
                result.setType(ExecutionResult.ExecutionType.REASONING);
                result.setOutput(action.content);
                result.setReasoning(action.thinking);
                break;

            case ERROR:
            default:
                result.setSuccess(false);
                result.setType(ExecutionResult.ExecutionType.ERROR);
                result.setError(action.error);
                break;
        }

        return result;
    }

    private String extractTag(String text, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";

        int start = text.indexOf(startTag);
        int end = text.indexOf(endTag);

        if (start != -1 && end != -1 && end > start) {
            return text.substring(start + startTag.length(), end).trim();
        }
        return null;
    }

    private String extractFinalAnswer(String text) {
        String answer = extractTag(text, "final_answer");
        if (answer != null) return answer;

        // 尝试直接提取
        int start = text.indexOf("<final_answer>");
        if (start != -1) {
            return text.substring(start + 14).trim();
        }
        return text;
    }

    private String extractToolName(String text) {
        String toolCall = extractTag(text, "tool_call");
        if (toolCall == null) return null;

        try {
            Map<String, Object> obj = JSON.parseObject(toolCall);
            return (String) obj.get("name");
        } catch (Exception e) {
            log.error("Failed to extract tool name: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> extractToolArgs(String text) {
        String toolCall = extractTag(text, "tool_call");
        if (toolCall == null) return new HashMap<>();

        try {
            Map<String, Object> obj = JSON.parseObject(toolCall);
            Object args = obj.get("arguments");
            if (args instanceof Map) {
                return (Map<String, Object>) args;
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Failed to extract tool args: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return (String) value;
        try {
            return JSON.toJSONString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    /**
     * 动作数据结构
     */
    @Data
    private static class Action {
        enum Type {
            FINAL_ANSWER,
            TOOL_CALL,
            REASONING,
            ERROR
        }

        Type type;
        String thinking;
        String content;
        String toolName;
        Map<String, Object> toolArgs;
        String error;
    }

    @Data
    public static class ReActConfig {
        private int maxRetries = 3;
        private double temperature = 0.7;
        private boolean enableThinking = true;
    }

    /**
     * 模型客户端接口
     */
    public interface ModelClient {
        String chat(String prompt, ExecutionContext context);
    }

    /**
     * 工具执行器接口
     */
    public interface ToolExecutor {
        Map<String, Object> execute(String toolName, Object args, ExecutionContext context);
    }
}

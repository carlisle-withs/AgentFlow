package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.workflow.engine.constants.MsgTypeEnum;
import com.iflytek.astron.workflow.engine.domain.chain.Node;
import com.iflytek.astron.workflow.engine.integration.model.ModelServiceClient;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmResVo;
import com.iflytek.astron.workflow.engine.integration.plugins.PluginServiceClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct (推理-行动) 循环引擎
 * 实现 Agent 的自主推理与工具决策机制
 */
@Slf4j
public class ReActLoop {

    private final ModelServiceClient modelClient;
    private final PluginServiceClient pluginClient;
    private final AgentContext context;

    public ReActLoop(ModelServiceClient modelClient, PluginServiceClient pluginClient, AgentContext context) {
        this.modelClient = modelClient;
        this.pluginClient = pluginClient;
        this.context = context;
    }

    /**
     * 执行完整的 ReAct 循环
     * 内部自动管理循环，直到达到终止条件
     */
    public void execute() {
        log.info("Starting ReAct loop: maxIterations={}", context.getMaxIterations());

        while (!context.isTerminal() && context.getCurrentIteration() < context.getMaxIterations()) {
            step();
        }

        // 检查是否达到最大迭代
        if (context.getCurrentIteration() >= context.getMaxIterations() && !context.isTerminal()) {
            log.warn("ReAct loop reached max iterations: {}", context.getMaxIterations());
            context.setTerminated("max_iterations");
            context.getOutputs().put("error", "达到最大迭代次数，任务未完成");
            context.getOutputs().put("partial_result", context.getMemory());
        }

        log.info("ReAct loop finished: iterations={}, reason={}",
                context.getCurrentIteration(), context.getTerminationReason());
    }

    /**
     * 执行一步 ReAct 循环
     */
    public void step() {
        context.incrementIteration();

        try {
            // 回调：节点开始处理
            context.getNodeState().callback().onNodeStart(
                    0,
                    context.getNodeState().node().getId(),
                    context.getNodeState().node().getData().getNodeMeta().getAliasName()
            );

            // 1. Think - 构建 Prompt 并调用 LLM
            String prompt = buildPrompt();
            LlmResVo llmOutput = callLLM(prompt);

            // 2. Parse - 解析 LLM 输出
            ActionParser.Action action = ActionParser.parse(llmOutput.content());

            // 保存思考内容到记忆
            if (action.getThinking() != null && !action.getThinking().isEmpty()) {
                context.addMemory("reasoning", action.getThinking(), null);
                context.addMessage(MsgTypeEnum.THINKING, action.getThinking());

                // 回调：推送思考过程
                context.getNodeState().callback().onNodeProcess(
                        0,
                        context.getNodeState().node().getId(),
                        context.getNodeState().node().getData().getNodeMeta().getAliasName(),
                        action.getThinking(),
                        null
                );
            }

            // 保存 LLM 响应到历史
            context.addMessage(MsgTypeEnum.ASSISTANT, action.getRawText() != null ? action.getRawText() : llmOutput.content());

            // 3. Act - 根据动作类型执行
            executeAction(action);

        } catch (Exception e) {
            log.error("ReAct loop error at iteration {}: {}", context.getCurrentIteration(), e.getMessage(), e);
            context.addMemory("error", e.getMessage(), null);
            handleError(e);
        }
    }

    /**
     * 构建 ReAct Prompt
     */
    private String buildPrompt() {
        Node node = context.getNodeState().node();
        Map<String, Object> nodeParam = node.getData().getNodeParam();

        StringBuilder prompt = new StringBuilder();

        // 1. 系统提示（包含角色和规则）
        String systemPrompt = buildSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            prompt.append(systemPrompt).append("\n\n");
        }

        // 2. 可用工具描述
        if (!context.getAvailableTools().isEmpty()) {
            prompt.append("## 可用工具\n");
            prompt.append("你可以调用以下工具来完成任务：\n\n");
            for (String toolId : context.getAvailableTools()) {
                prompt.append(String.format("- %s\n", toolId));
            }
            prompt.append("\n");
        }

        // 3. 当前上下文（输入变量）
        prompt.append("## 当前输入\n");
        prompt.append(formatContext(context.getInputs()));
        prompt.append("\n\n");

        // 4. 记忆（历史推理和工具结果）
        if (!context.getMemory().isEmpty()) {
            prompt.append("## 执行历史\n");
            for (AgentContext.MemoryItem item : context.getMemory()) {
                prompt.append(String.format("【%s】%s\n", item.getType(), item.getContent()));
            }
            prompt.append("\n");
        }

        // 5. 任务指令
        String task = getString(nodeParam, "taskPrompt", "请完成上述任务。");
        prompt.append("## 任务\n").append(task).append("\n\n");

        // 6. 输出格式说明
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
     * 构建系统提示
     */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        // 角色定义
        String role = context.getPromptSegments().get("role");
        if (role != null && !role.isEmpty()) {
            sb.append("## 角色\n").append(role).append("\n\n");
        }

        // 约束规则
        String constraint = context.getPromptSegments().get("constraint");
        if (constraint != null && !constraint.isEmpty()) {
            sb.append("## 约束规则\n").append(constraint).append("\n\n");
        }

        // 如果没有分段，使用完整的 systemPrompt
        if (sb.length() == 0 && context.getSystemPrompt() != null) {
            sb.append(context.getSystemPrompt());
        }

        return sb.toString();
    }

    /**
     * 调用 LLM
     */
    private LlmResVo callLLM(String prompt) {
        Node node = context.getNodeState().node();

        // 构建请求
        ModelSelector.ModelConfig modelConfig = new ModelSelector.ModelConfig();
        modelConfig.setModelType(context.getModelType());
        modelConfig.setModelId(context.getModelId());
        modelConfig.setTemperature(context.getTemperature());
        modelConfig.setEnableThinking(context.isEnableThinking());

        // 使用 ModelServiceClient 调用
        com.iflytek.astron.workflow.engine.integration.model.bo.LlmReqBo req =
                new com.iflytek.astron.workflow.engine.integration.model.bo.LlmReqBo();
        req.setNodeId(node.getId());
        req.setUserMsg(prompt);
        req.setModel(context.getModelId());
        req.setTemperature(context.getTemperature());

        // 设置额外参数
        if (context.isEnableThinking()) {
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("enable_thinking", true);
            req.setExtraParams(extraParams);
        }

        return modelClient.chatCompletion(req, null);
    }

    /**
     * 执行动作
     */
    private void executeAction(ActionParser.Action action) {
        if (action.isFinalAnswer()) {
            // 任务完成
            context.setTerminated("completed");
            context.getOutputs().put("result", action.getContent());
            context.addMemory("result", action.getContent(), null);

            // 回调：节点结束
            context.getNodeState().callback().onNodeEnd(
                    context.getNodeState().node().getId(),
                    context.getNodeState().node().getData().getNodeMeta().getAliasName(),
                    null
            );
            return;
        }

        if (action.isToolCall()) {
            // 工具调用成功，重置无工具调用计数
            context.resetNoToolIterationCount();
            executeToolCall(action);
            return;
        }

        if (action.isError()) {
            // 错误处理
            context.setTerminated("error: " + action.getErrorMessage());
            return;
        }

        // CONTINUE - 继续推理，检查是否无进展
        context.addMemory("reasoning", action.getContent(), null);
        context.checkNoProgress();
    }

    /**
     * 执行工具调用
     */
    private void executeToolCall(ActionParser.Action action) {
        try {
            log.info("Executing tool: {} with args: {}", action.getToolName(), action.getToolArguments());

            // 构建工具调用输入
            Map<String, Object> toolInputs = new HashMap<>();
            if (action.getToolArguments() != null) {
                toolInputs.putAll(action.getToolArguments());
            }

            // 添加上下文信息
            toolInputs.put("_agent_context", buildAgentContext());

            // 通过 PluginServiceClient 调用工具
            Map<String, Object> toolResult = pluginClient.toolCall(context.getNodeState(), toolInputs);

            // 工具调用成功
            context.incrementToolSuccessCount();
            context.setLastToolName(action.getToolName());

            // 保存工具结果到记忆
            context.addMemory("tool_call", String.format("%s(%s) -> %s",
                    action.getToolName(),
                    JSON.toJSONString(action.getToolArguments()),
                    JSON.toJSONString(toolResult)), null);

            context.addMemory("observation", JSON.toJSONString(toolResult), null);

            // 将工具结果添加到上下文，供后续推理使用
            context.getInputs().put(action.getToolName() + "_result", toolResult);

            // 回调：推送工具执行结果
            context.getNodeState().callback().onNodeProcess(
                    0,
                    context.getNodeState().node().getId(),
                    context.getNodeState().node().getData().getNodeMeta().getAliasName(),
                    JSON.toJSONString(toolResult),
                    null
            );

        } catch (Exception e) {
            log.error("Tool call failed: {} - {}", action.getToolName(), e.getMessage(), e);

            // 工具调用失败
            context.incrementToolFailureCount();
            context.addMemory("error", String.format("Tool %s failed: %s", action.getToolName(), e.getMessage()), null);

            // 将错误信息注入上下文，让 LLM 知道工具调用失败了
            context.getInputs().put(action.getToolName() + "_error", e.getMessage());

            // 检查工具连续失败次数
            if (context.getToolFailureCount() >= context.getMaxToolConsecutiveFailures()) {
                log.warn("Tool {} failed {} times consecutively, terminating",
                        action.getToolName(), context.getToolFailureCount());
                context.setTerminated("tool_consecutive_failures");
            }
        }
    }

    /**
     * 处理错误
     */
    private void handleError(Exception e) {
        // 错误已被记录到记忆，循环将继续执行
        // 如果需要强制终止，可以在子类中重写此方法
    }

    /**
     * 构建 Agent 上下文信息
     */
    private Map<String, Object> buildAgentContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("nodeId", context.getNodeState().node().getId());
        ctx.put("iteration", context.getCurrentIteration());
        ctx.put("maxIterations", context.getMaxIterations());
        ctx.put("availableTools", context.getAvailableTools());
        return ctx;
    }

    /**
     * 格式化上下文信息
     */
    private String formatContext(Map<String, Object> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "(无输入)";
        }

        StringBuilder sb = new StringBuilder();
        inputs.forEach((key, value) -> {
            sb.append(String.format("- %s: %s\n", key, formatValue(value)));
        });
        return sb.toString();
    }

    /**
     * 格式化值
     */
    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return (String) value;
        try {
            return JSON.toJSONString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
}

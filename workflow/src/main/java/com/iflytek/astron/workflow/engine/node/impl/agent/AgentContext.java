package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.iflytek.astron.workflow.engine.constants.MsgTypeEnum;
import com.iflytek.astron.workflow.engine.domain.NodeRunResult;
import com.iflytek.astron.workflow.engine.domain.NodeState;
import com.iflytek.astron.workflow.engine.integration.model.LlmChatHistory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行上下文
 * 管理 Agent 的状态、记忆、对话历史和配置信息
 */
@Slf4j
@Data
public class AgentContext {

    /**
     * 节点状态
     */
    private NodeState nodeState;

    /**
     * 输入变量（来自上游节点）
     */
    private Map<String, Object> inputs;

    /**
     * Agent 记忆（历史推理 + 工具执行结果）
     */
    private List<MemoryItem> memory;

    /**
     * 对话历史
     */
    private List<ChatMessage> history;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * Prompt 分段（支持分段管理）
     */
    private Map<String, String> promptSegments;

    /**
     * 最大迭代次数
     */
    private int maxIterations;

    /**
     * 当前迭代次数
     */
    private int currentIteration;

    /**
     * 是否达到终止状态
     */
    private boolean terminal;

    /**
     * 终止原因
     */
    private String terminationReason;

    /**
     * 最终输出
     */
    private Map<String, Object> outputs;

    /**
     * 可用工具列表
     */
    private List<String> availableTools;

    /**
     * 模型类型 (qwen/minimax/private)
     */
    private String modelType;

    /**
     * 具体模型 ID
     */
    private String modelId;

    /**
     * 是否启用深度思考
     */
    private boolean enableThinking;

    /**
     * 温度参数
     */
    private double temperature;

    /**
     * 创建 AgentContext
     */
    public static AgentContext create(NodeState nodeState, Map<String, Object> inputs) {
        AgentContext context = new AgentContext();
        context.nodeState = nodeState;
        context.inputs = inputs;
        context.memory = new ArrayList<>();
        context.history = new ArrayList<>();
        context.promptSegments = new HashMap<>();
        context.availableTools = new ArrayList<>();
        context.currentIteration = 0;
        context.terminal = false;
        context.outputs = new HashMap<>();
        context.maxIterations = 10;
        context.temperature = 0.7;
        context.enableThinking = false;
        return context;
    }

    /**
     * 添加记忆项
     */
    public void addMemory(String type, String content, Map<String, Object> metadata) {
        MemoryItem item = new MemoryItem(type, content, metadata);
        this.memory.add(item);
    }

    /**
     * 添加对话消息
     */
    public void addMessage(MsgTypeEnum role, String content) {
        this.history.add(new ChatMessage(role, content));
    }

    /**
     * 获取渲染后的系统提示
     */
    public String getRenderedSystemPrompt() {
        if (promptSegments.isEmpty()) {
            return systemPrompt;
        }

        StringBuilder sb = new StringBuilder();
        promptSegments.forEach((key, value) -> {
            sb.append(value).append("\n\n");
        });
        return sb.toString().trim();
    }

    /**
     * 增加迭代次数
     */
    public boolean incrementIteration() {
        this.currentIteration++;
        if (this.currentIteration >= this.maxIterations) {
            this.terminal = true;
            this.terminationReason = "max_iterations";
            return false;
        }
        return true;
    }

    /**
     * 设置终止状态
     */
    public void setTerminated(String reason) {
        this.terminal = true;
        this.terminationReason = reason;
    }

    /**
     * 构建执行结果
     */
    public NodeRunResult buildResult() {
        NodeRunResult result = new NodeRunResult();
        result.setInputs(this.inputs);
        result.setOutputs(this.outputs);
        result.setStatus(com.iflytek.astron.workflow.engine.constants.NodeExecStatusEnum.SUCCESS);

        if (this.terminal && "max_iterations".equals(this.terminationReason)) {
            log.warn("Agent reached max iterations: {}", this.maxIterations);
        }

        return result;
    }

    /**
     * 记忆项
     */
    @Data
    public static class MemoryItem {
        /**
         * 记忆类型 (reasoning/action/observation/result)
         */
        private String type;

        /**
         * 记忆内容
         */
        private String content;

        /**
         * 额外元数据
         */
        private Map<String, Object> metadata;

        public MemoryItem(String type, String content, Map<String, Object> metadata) {
            this.type = type;
            this.content = content;
            this.metadata = metadata;
        }
    }

    /**
     * 对话消息
     */
    @Data
    public static class ChatMessage {
        private MsgTypeEnum role;
        private String content;
        private long timestamp;

        public ChatMessage(MsgTypeEnum role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
    }
}

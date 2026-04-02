package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.iflytek.astron.workflow.engine.constants.MsgTypeEnum;
import com.iflytek.astron.workflow.engine.constants.NodeExecStatusEnum;
import com.iflytek.astron.workflow.engine.constants.NodeTypeEnum;
import com.iflytek.astron.workflow.engine.domain.NodeRunResult;
import com.iflytek.astron.workflow.engine.domain.NodeState;
import com.iflytek.astron.workflow.engine.domain.chain.Node;
import com.iflytek.astron.workflow.engine.engine.util.VariableTemplateRender;
import com.iflytek.astron.workflow.engine.integration.model.ModelServiceClient;
import com.iflytek.astron.workflow.engine.integration.plugins.PluginServiceClient;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.util.FlowUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 节点执行器
 * 支持 ReAct 推理-行动循环机制，实现复杂任务中的自主拆解与工具决策
 *
 * <p>功能特性：
 * <ul>
 *   <li>ReAct 循环机制 - 推理、行动、观察的循环执行</li>
 *   <li>动态上下文注入 - 支持 Prompt 分段管理和动态变量替换</li>
 *   <li>多模型支持 - Qwen/Minimax/私有化模型无感切换</li>
 *   <li>工具决策 - 自主判断何时调用工具以及调用哪个工具</li>
 *   <li>多轮对话 - 支持与 LLM 的多轮交互</li>
 * </ul>
 *
 * <p>节点配置示例 (nodeParam)：
 * <pre>
 * {
 *   "modelType": "qwen",              // 模型类型
 *   "modelId": "qwen-plus",           // 具体模型
 *   "maxIterations": 10,               // 最大迭代次数
 *   "enableThinking": true,             // 启用深度思考
 *   "temperature": 0.7,               // 采样温度
 *   "systemPrompt": "你是一个助手...",  // 系统提示
 *   "promptSegments": {                // Prompt 分段
 *     "role": "你扮演的角色是...",
 *     "constraint": "你必须遵守的规则是...",
 *     "task": "你需要完成的任务是..."
 *   },
 *   "availableTools": ["pluginId1", "pluginId2"]  // 可用工具列表
 * }
 * </pre>
 */
@Slf4j
@Component
public class AgentNodeExecutor extends AbstractNodeExecutor {

    private final ModelServiceClient modelClient;
    private final PluginServiceClient pluginClient;

    @Autowired
    public AgentNodeExecutor(ModelServiceClient modelClient, PluginServiceClient pluginClient) {
        this.modelClient = modelClient;
        this.pluginClient = pluginClient;
    }

    @Override
    public NodeTypeEnum getNodeType() {
        return NodeTypeEnum.AGENT;
    }

    @Override
    protected NodeRunResult executeNode(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Node node = nodeState.node();
        Map<String, Object> nodeParam = node.getData().getNodeParam();

        log.info("Starting Agent node execution: nodeId={}, maxIterations={}",
                node.getId(), getMaxIterations(nodeParam));

        try {
            // 1. 初始化 Agent 上下文
            AgentContext context = initContext(nodeState, inputs, nodeParam);

            // 2. 创建 ReAct 循环引擎
            ReActLoop reactLoop = new ReActLoop(modelClient, pluginClient, context);

            // 3. 执行 ReAct 循环直到终止
            while (!context.isTerminal()) {
                boolean continueLoop = reactLoop.step();
                if (!continueLoop) {
                    break;
                }

                // 安全检查：防止无限循环
                if (context.getCurrentIteration() >= context.getMaxIterations()) {
                    log.warn("Agent node reached max iterations: {}", context.getMaxIterations());
                    context.setTerminated("max_iterations");
                    break;
                }
            }

            // 4. 构建并返回结果
            return buildResult(context, inputs);

        } catch (Exception e) {
            log.error("Agent node execution failed: nodeId={}", node.getId(), e);
            return buildErrorResult(nodeState, inputs, e);
        }
    }

    /**
     * 初始化 Agent 上下文
     */
    private AgentContext initContext(NodeState nodeState, Map<String, Object> inputs, Map<String, Object> nodeParam) {
        AgentContext context = AgentContext.create(nodeState, inputs);

        // 1. 基本配置
        context.setMaxIterations(getMaxIterations(nodeParam));
        context.setModelType(getString(nodeParam, "modelType", "qwen"));
        context.setModelId(getString(nodeParam, "modelId", "qwen-plus"));
        context.setTemperature(getDouble(nodeParam, "temperature", 0.7));
        context.setEnableThinking(getBoolean(nodeParam, "enableThinking", false));

        // 2. 系统提示（支持分段管理）
        String systemPrompt = getString(nodeParam, "systemPrompt", null);
        if (systemPrompt != null) {
            context.setSystemPrompt(VariableTemplateRender.render(systemPrompt, inputs));
        }

        // 3. Prompt 分段
        Object promptSegmentsObj = nodeParam.get("promptSegments");
        if (promptSegmentsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> promptSegments = (Map<String, Object>) promptSegmentsObj;
            promptSegments.forEach((key, value) -> {
                if (value != null) {
                    String rendered = VariableTemplateRender.render(String.valueOf(value), inputs);
                    context.getPromptSegments().put(key, rendered);
                }
            });
        }

        // 4. 可用工具列表
        List<String> availableTools = getList(nodeParam, "availableTools");
        context.setAvailableTools(availableTools);

        // 5. 添加初始用户输入到历史
        Object userInputObj = nodeParam.get("userInput");
        if (userInputObj != null) {
            String userInput = String.valueOf(userInputObj);
            if (!userInput.isEmpty()) {
                String renderedInput = VariableTemplateRender.render(userInput, inputs);
                context.addMessage(MsgTypeEnum.USER, renderedInput);
            }
        }

        log.info("Agent context initialized: maxIterations={}, modelType={}, tools={}",
                context.getMaxIterations(), context.getModelType(), context.getAvailableTools().size());

        return context;
    }

    /**
     * 构建成功结果
     */
    private NodeRunResult buildResult(AgentContext context, Map<String, Object> inputs) {
        NodeRunResult result = new NodeRunResult();
        result.setInputs(inputs);
        result.setOutputs(context.getOutputs());
        result.setStatus(NodeExecStatusEnum.SUCCESS);

        // 添加执行统计信息
        Map<String, Object> stats = new HashMap<>();
        stats.put("iterations", context.getCurrentIteration());
        stats.put("terminationReason", context.getTerminationReason());
        stats.put("memorySize", context.getMemory().size());
        result.getOutputs().put("_agent_stats", stats);

        log.info("Agent execution completed: iterations={}, reason={}",
                context.getCurrentIteration(), context.getTerminationReason());

        return result;
    }

    /**
     * 构建错误结果
     */
    private NodeRunResult buildErrorResult(NodeState nodeState, Map<String, Object> inputs, Exception e) {
        NodeRunResult result = new NodeRunResult();
        result.setInputs(inputs);
        result.setStatus(NodeExecStatusEnum.ERR_INTERUPT);

        Map<String, Object> errorOutputs = new HashMap<>();
        errorOutputs.put("error", e.getMessage());
        errorOutputs.put("errorType", e.getClass().getSimpleName());
        result.setErrorOutputs(errorOutputs);

        // 回调错误
        nodeState.callback().onNodeInterrupt(
                FlowUtil.genInterruptEventId(),
                errorOutputs,
                nodeState.node().getId(),
                nodeState.node().getData().getNodeMeta().getAliasName(),
                "AGENT_EXECUTION_ERROR",
                "error",
                false
        );

        return result;
    }

    // ==================== 辅助方法 ====================

    private int getMaxIterations(Map<String, Object> nodeParam) {
        Object value = nodeParam.get("maxIterations");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 10; // 默认 10 次
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            return (List<String>) value;
        }
        if (value instanceof String) {
            // 支持逗号分隔的字符串
            List<String> result = new ArrayList<>();
            for (String item : ((String) value).split(",")) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
}

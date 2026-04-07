package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.iflytek.astron.workflow.engine.constants.MsgTypeEnum;
import com.iflytek.astron.workflow.engine.constants.NodeExecStatusEnum;
import com.iflytek.astron.workflow.engine.constants.NodeTypeEnum;
import com.iflytek.astron.workflow.engine.domain.NodeRunResult;
import com.iflytek.astron.workflow.engine.domain.NodeState;
import com.iflytek.astron.workflow.engine.domain.chain.Node;
import com.iflytek.astron.workflow.engine.util.VariableTemplateRender;
import com.iflytek.astron.workflow.engine.integration.model.ModelServiceClient;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmReqBo;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmResVo;
import com.iflytek.astron.workflow.engine.integration.plugins.PluginServiceClient;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultMemory;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultPlanner;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultReAct;
import com.iflytek.astron.workflow.engine.node.impl.agent.impl.DefaultReflect;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.Task;
import com.iflytek.astron.workflow.engine.observability.AgentMetrics;
import com.iflytek.astron.workflow.engine.observability.AgentTracer;
import com.iflytek.astron.workflow.engine.observability.TraceContext;
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
 *   <li>Planner 规划 - 支持任务拆解为可执行步骤（新架构）</li>
 *   <li>Reflect 反思 - 支持结果评估和计划修正（新架构）</li>
 *   <li>Memory 记忆 - 支持上下文压缩和记忆管理（新架构）</li>
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
 *
 * <p>新架构配置 (useNewArchitecture=true)：
 * <pre>
 * {
 *   "useNewArchitecture": true,        // 启用新架构
 *   "modelId": "qwen-plus",           // 模型ID
 *   "temperature": 0.7,               // 采样温度
 *   "taskPrompt": "请完成以下任务...", // 任务描述
 *   "taskGoal": "目标描述",           // 任务目标（可选）
 *   "maxContextTokens": 8000,         // 最大上下文Token
 *   "maxSteps": 20,                   // 最大执行步骤
 *   "availableTools": ["pluginId1"]   // 可用工具列表
 * }
 * </pre>
 */
@Slf4j
@Component
public class AgentNodeExecutor extends AbstractNodeExecutor {

    private final ModelServiceClient modelClient;
    private final PluginServiceClient pluginClient;

    /**
     * 当前执行的 NodeState（用于工具调用）
     */
    private NodeState currentNodeState;

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

        // 创建追踪上下文
        TraceContext.create();
        String traceId = TraceContext.getTraceId();

        long startTime = System.currentTimeMillis();
        log.info("Starting Agent node execution: nodeId={}, maxIterations={}, traceId={}",
                node.getId(), getMaxIterations(nodeParam), traceId);

        // 启动追踪 Span
        try (TraceContext.SpanWrapper span = AgentTracer.startAgentSpan(
                node.getId(),
                node.getData().getNodeMeta().getAliasName())) {

            // 判断使用新架构还是旧架构
            boolean useNewArchitecture = getBoolean(nodeParam, "useNewArchitecture", false);

            NodeRunResult result;
            try {
                if (useNewArchitecture) {
                    // 使用新架构：Planner + ReAct + Reflect + Memory
                    result = executeWithNewArchitecture(nodeState, inputs, nodeParam);
                } else {
                    // 使用旧架构：ReActLoop
                    result = executeWithReActLoop(nodeState, inputs, nodeParam);
                }
            } catch (Exception e) {
                log.error("Agent node execution failed: nodeId={}", node.getId(), e);
                result = buildErrorResult(nodeState, inputs, e);
            }

            // 记录执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            AgentMetrics.recordAgentExecutionTime(executionTime);

            // 添加追踪信息到结果
            if (result.getOutputs() == null) {
                result.setOutputs(new HashMap<>());
            }
            result.getOutputs().put("_traceId", traceId);
            result.getOutputs().put("_executionTimeMs", executionTime);

            // 获取完整指标快照
            Map<String, Object> metrics = AgentMetrics.snapshot();
            result.getOutputs().put("_metrics", metrics);

            log.info("Agent node execution completed: nodeId={}, traceId={}, executionTime={}ms",
                    node.getId(), traceId, executionTime);

            return result;
        } finally {
            // 清理追踪上下文
            TraceContext.clear();
        }
    }

    /**
     * 使用新架构执行 Agent
     * 架构：Planner(规划) + ReAct(执行) + Reflect(反思) + Memory(记忆)
     */
    private NodeRunResult executeWithNewArchitecture(NodeState nodeState, Map<String, Object> inputs,
                                                     Map<String, Object> nodeParam) throws Exception {
        Node node = nodeState.node();

        // 1. 初始化组件
        DefaultMemory memory = new DefaultMemory();
        DefaultPlanner planner = new DefaultPlanner();
        DefaultReflect reflect = new DefaultReflect();

        // 2. 创建 ModelClient 和 ToolExecutor 适配器
        DefaultReAct.ModelClient modelClientAdapter = (prompt, context) -> {
            return callLLM(node, prompt, nodeParam, context);
        };

        DefaultReAct.ToolExecutor toolExecutorAdapter = (toolName, args, context) -> {
            return callTool(toolName, args);
        };

        DefaultReAct react = new DefaultReAct(modelClientAdapter, toolExecutorAdapter);

        // 3. 创建 Agent
        Agent.AgentConfig agentConfig = new Agent.AgentConfig();
        agentConfig.setMaxContextTokens(getInt(nodeParam, "maxContextTokens", 8000));
        agentConfig.setMaxSteps(getInt(nodeParam, "maxSteps", 20));
        agentConfig.setSystemPrompt(buildSystemPrompt(nodeParam, inputs));

        com.iflytek.astron.workflow.engine.node.impl.agent.Agent agent =
                new com.iflytek.astron.workflow.engine.node.impl.agent.Agent(planner, react, reflect, memory, agentConfig);

        // 4. 构建任务
        Task task = new Task();
        task.setDescription(getString(nodeParam, "taskPrompt", "请完成以下任务"));
        task.setGoal(getString(nodeParam, "taskGoal", null));

        // 5. 获取可用工具
        List<String> availableTools = getList(nodeParam, "availableTools");

        // 6. 执行
        log.info("Executing with new Agent architecture: tools={}", availableTools.size());
        Agent.AgentOutput output = agent.execute(task, availableTools);

        // 7. 构建结果
        return buildNewArchitectureResult(nodeState, inputs, output, agent.getMemory());

    }

    /**
     * 调用 LLM（供新架构使用）
     * 集成全链路追踪和指标采集
     */
    private String callLLM(Node node, String prompt, Map<String, Object> nodeParam,
                          DefaultReAct.ExecutionContext context) {
        String modelId = getString(nodeParam, "modelId", "qwen-plus");
        int promptLength = prompt != null ? prompt.length() : 0;
        long startTime = System.currentTimeMillis();

        // 启动 LLM 追踪 Span
        try (TraceContext.SpanWrapper span = AgentTracer.startLlmSpan(modelId, "reasoning")) {
            try {
                LlmReqBo req = new LlmReqBo();
                req.setNodeId(node.getId());
                req.setUserMsg(prompt);
                req.setModel(modelId);
                req.setTemperature(getDouble(nodeParam, "temperature", 0.7));

                LlmResVo res = modelClient.chatCompletion(req, null);
                String response = res.content();

                // 计算延迟和 Token（估算）
                long latencyMs = System.currentTimeMillis() - startTime;
                int promptTokens = estimateTokens(promptLength);
                int responseTokens = estimateTokens(response != null ? response.length() : 0);

                // 记录 LLM 指标
                AgentTracer.recordLlmCall(
                        modelId, "reasoning",
                        promptLength, promptTokens,
                        responseTokens, latencyMs, true
                );

                span.setAttribute("llm.success", true);
                span.setAttribute("llm.latencyMs", latencyMs);
                span.setAttribute("llm.promptTokens", promptTokens);
                span.setAttribute("llm.responseTokens", responseTokens);

                return response;

            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - startTime;

                // 记录失败指标
                AgentTracer.recordLlmCall(
                        modelId, "reasoning",
                        promptLength, estimateTokens(promptLength),
                        0, latencyMs, false
                );

                span.setAttribute("llm.success", false);
                span.setAttribute("llm.error", e.getMessage());

                log.error("LLM call failed: {}", e.getMessage(), e);
                throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 调用工具（供新架构使用）
     * 集成全链路追踪和指标采集
     */
    private Map<String, Object> callTool(String toolName, Object args) {
        long startTime = System.currentTimeMillis();

        // 启动工具追踪 Span
        try (TraceContext.SpanWrapper span = AgentTracer.startToolSpan(toolName, "plugin")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> toolInputs = (Map<String, Object>) args;
                if (toolInputs == null) {
                    toolInputs = new HashMap<>();
                }

                Map<String, Object> result = pluginClient.toolCall(currentNodeState, toolInputs);

                // 记录工具调用指标
                long latencyMs = System.currentTimeMillis() - startTime;
                AgentTracer.recordToolCall(toolName, "plugin", latencyMs, true);

                span.setAttribute("tool.success", true);
                span.setAttribute("tool.latencyMs", latencyMs);

                return result;

            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - startTime;

                // 记录失败指标
                AgentTracer.recordToolCall(toolName, "plugin", latencyMs, false);

                span.setAttribute("tool.success", false);
                span.setAttribute("tool.error", e.getMessage());

                log.error("Tool call failed: {} - {}", toolName, e.getMessage(), e);
                throw new RuntimeException("Tool call failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 使用旧架构 ReActLoop 执行
     */
    private NodeRunResult executeWithReActLoop(NodeState nodeState, Map<String, Object> inputs,
                                              Map<String, Object> nodeParam) throws Exception {
        // 1. 初始化 Agent 上下文
        AgentContext context = initContext(nodeState, inputs, nodeParam);

        // 2. 创建 ReAct 循环引擎并执行
        ReActLoop reactLoop = new ReActLoop(modelClient, pluginClient, context);
        reactLoop.execute();  // 内部自动管理循环

        // 3. 构建并返回结果
        return buildResult(context, inputs);
    }

    /**
     * 构建新架构的执行结果
     */
    private NodeRunResult buildNewArchitectureResult(NodeState nodeState, Map<String, Object> inputs,
                                                      Agent.AgentOutput output,
                                                      com.iflytek.astron.workflow.engine.node.impl.agent.core.Memory memory) {
        NodeRunResult result = new NodeRunResult();
        result.setInputs(inputs);
        result.setStatus(NodeExecStatusEnum.SUCCESS);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", output.getResult());
        outputs.put("success", output.isSuccess());
        outputs.put("message", output.getMessage());

        // 添加执行统计
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSteps", output.getTotalSteps());
        stats.put("completedSteps", output.getCompletedSteps());
        stats.put("progress", output.getProgress());
        stats.put("memorySize", output.getMemorySize());
        stats.put("planId", output.getPlanId());
        outputs.put("_agent_stats", stats);

        result.setOutputs(outputs);

        log.info("Agent (new architecture) execution completed: success={}, steps={}/{}",
                output.isSuccess(), output.getCompletedSteps(), output.getTotalSteps());

        return result;
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
                500,
                "AGENT_EXECUTION_ERROR",
                false
        );

        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建系统提示（供新架构使用）
     */
    private String buildSystemPrompt(Map<String, Object> nodeParam, Map<String, Object> inputs) {
        StringBuilder prompt = new StringBuilder();

        // 系统提示
        String systemPrompt = getString(nodeParam, "systemPrompt", null);
        if (systemPrompt != null) {
            prompt.append(VariableTemplateRender.render(systemPrompt, inputs)).append("\n\n");
        }

        // Prompt 分段
        Object promptSegmentsObj = nodeParam.get("promptSegments");
        if (promptSegmentsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> promptSegments = (Map<String, Object>) promptSegmentsObj;
            promptSegments.forEach((key, value) -> {
                if (value != null) {
                    String rendered = VariableTemplateRender.render(String.valueOf(value), inputs);
                    prompt.append(rendered).append("\n\n");
                }
            });
        }

        // 默认提示
        if (prompt.length() == 0) {
            prompt.append("你是一个智能助手，负责完成用户任务。");
        }

        return prompt.toString();
    }

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

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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

    /**
     * 估算 Token 数量（简单估算：中文按字符数，英文按空格分词）
     * 实际项目中应使用 tiktoken 或类似库
     */
    private int estimateTokens(int charCount) {
        // 简单估算：中文字符约 1.5 Token，英文约 0.25 Token
        // 这里简化为 charCount / 4
        return Math.max(1, charCount / 4);
    }

    /**
     * 估算字符串的 Token 数量
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return estimateTokens(text.length());
    }
}

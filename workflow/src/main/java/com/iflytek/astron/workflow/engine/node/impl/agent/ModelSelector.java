package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.iflytek.astron.workflow.engine.domain.chain.Node;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmReqBo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多模型选择器
 * 支持 Qwen / Minimax / 私有化模型的无感切换
 */
@Slf4j
@Component
public class ModelSelector {

    /**
     * 默认模型类型
     */
    private static final String DEFAULT_MODEL_TYPE = "qwen";

    /**
     * 模型类型常量
     */
    public static final String MODEL_TYPE_QWEN = "qwen";
    public static final String MODEL_TYPE_MINIMAX = "minimax";
    public static final String MODEL_TYPE_PRIVATE = "private";
    public static final String MODEL_TYPE_OPENAI = "openai";

    /**
     * Qwen 模型配置
     */
    @Value("${agent.model.qwen.url:https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation}")
    private String qwenUrl;

    @Value("${agent.model.qwen.api-key:}")
    private String qwenApiKey;

    @Value("${agent.model.qwen.model:qwen-plus}")
    private String qwenModel;

    /**
     * Minimax 模型配置
     */
    @Value("${agent.model.minimax.url:}")
    private String minimaxUrl;

    @Value("${agent.model.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${agent.model.minimax.model:}")
    private String minimaxModel;

    /**
     * 私有化模型配置
     */
    @Value("${agent.model.private.url:}")
    private String privateUrl;

    @Value("${agent.model.private.api-key:}")
    private String privateApiKey;

    @Value("${agent.model.private.model:}")
    private String privateModel;

    /**
     * OpenAI 兼容模型配置
     */
    @Value("${agent.model.openai.url:}")
    private String openaiUrl;

    @Value("${agent.model.openai.api-key:}")
    private String openaiApiKey;

    @Value("${agent.model.openai.model:gpt-4}")
    private String openaiModel;

    /**
     * 根据节点配置选择模型
     *
     * @param node 节点
     * @return 模型配置
     */
    public ModelConfig select(Node node) {
        Map<String, Object> nodeParam = node.getData().getNodeParam();

        String modelType = getString(nodeParam, "modelType", DEFAULT_MODEL_TYPE);
        String modelId = getString(nodeParam, "modelId", getDefaultModel(modelType));

        ModelConfig config = new ModelConfig();
        config.setModelType(modelType);
        config.setModelId(modelId);

        switch (modelType) {
            case MODEL_TYPE_QWEN -> {
                config.setUrl(qwenUrl);
                config.setApiKey(qwenApiKey);
            }
            case MODEL_TYPE_MINIMAX -> {
                config.setUrl(minimaxUrl);
                config.setApiKey(minimaxApiKey);
            }
            case MODEL_TYPE_PRIVATE -> {
                config.setUrl(privateUrl);
                config.setApiKey(privateApiKey);
            }
            case MODEL_TYPE_OPENAI -> {
                config.setUrl(openaiUrl);
                config.setApiKey(openaiApiKey);
            }
            default -> {
                // 默认使用 Qwen
                config.setUrl(qwenUrl);
                config.setApiKey(qwenApiKey);
                config.setModelId(qwenModel);
            }
        }

        // 从节点配置覆盖默认配置
        if (nodeParam.containsKey("url")) {
            config.setUrl((String) nodeParam.get("url"));
        }
        if (nodeParam.containsKey("apiKey")) {
            config.setApiKey((String) nodeParam.get("apiKey"));
        }

        // 设置额外参数
        config.setTemperature(getDouble(nodeParam, "temperature", 0.7));
        config.setMaxTokens(getInteger(nodeParam, "maxTokens", 2048));
        config.setEnableThinking(getBoolean(nodeParam, "enableThinking", false));

        log.info("Selected model: type={}, model={}, url={}", modelType, config.getModelId(), config.getUrl());
        return config;
    }

    /**
     * 构建 LLM 请求
     *
     * @param config   模型配置
     * @param systemPrompt 系统提示
     * @param userPrompt   用户提示
     * @param history      对话历史
     * @param node        节点
     * @return LLM 请求对象
     */
    public LlmReqBo buildRequest(ModelConfig config, String systemPrompt, String userPrompt,
                                  List<AgentContext.ChatMessage> history, Node node) {
        LlmReqBo req = new LlmReqBo();
        req.setNodeId(node.getId());
        req.setSystemMsg(systemPrompt);
        req.setUserMsg(userPrompt);
        req.setModel(config.getModelId());
        req.setUrl(config.getUrl());
        req.setApiKey(config.getApiKey());
        req.setSource(config.getModelType());
        req.setTemperature(config.getTemperature());
        req.setMaxTokens(config.getMaxTokens());

        // 转换历史消息
        if (history != null && !history.isEmpty()) {
            req.setHistory(history.stream()
                    .map(h -> new com.iflytek.astron.workflow.engine.integration.model.LlmChatHistory.ChatItem(
                            null, null, null, null, null))
                    .toList());
        }

        // 设置额外参数
        Map<String, Object> extraParams = new HashMap<>();
        if (config.isEnableThinking()) {
            extraParams.put("enable_thinking", true);
        }
        req.setExtraParams(extraParams);

        return req;
    }

    private String getDefaultModel(String modelType) {
        return switch (modelType) {
            case MODEL_TYPE_QWEN -> qwenModel;
            case MODEL_TYPE_MINIMAX -> minimaxModel;
            case MODEL_TYPE_PRIVATE -> privateModel;
            case MODEL_TYPE_OPENAI -> openaiModel;
            default -> qwenModel;
        };
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

    private int getInteger(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
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

    /**
     * 模型配置
     */
    @Data
    public static class ModelConfig {
        /**
         * 模型类型
         */
        private String modelType;

        /**
         * 具体模型 ID
         */
        private String modelId;

        /**
         * API URL
         */
        private String url;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 采样温度
         */
        private double temperature;

        /**
         * 最大 token 数
         */
        private int maxTokens;

        /**
         * 是否启用深度思考
         */
        private boolean enableThinking;
    }
}

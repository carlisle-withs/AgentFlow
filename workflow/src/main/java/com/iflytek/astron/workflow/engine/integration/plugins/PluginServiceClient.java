package com.iflytek.astron.workflow.engine.integration.plugins;

import com.iflytek.astron.workflow.engine.domain.NodeState;
import com.iflytek.astron.workflow.engine.domain.chain.Node;
import com.iflytek.astron.workflow.engine.integration.plugins.tts.TtsIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plugin Service Client with Registry Pattern
 * Replaces hardcoded plugin routing with a extensible plugin registry
 *
 * @author YiHui
 * @date 2025/12/4
 */
@Slf4j
@Service
public class PluginServiceClient {
    
    @Autowired
    private List<PluginExecutor> pluginExecutors;
    
    @Autowired
    private List<TtsIntegration> smartTTSIntegration;

    @Value("${tts.source:qwen}")
    private String ttsSource;
    
    private final Map<String, PluginExecutor> pluginRegistry = new HashMap<>();

    @PostConstruct
    public void init() {
        for (PluginExecutor executor : pluginExecutors) {
            pluginRegistry.put(executor.getType(), executor);
            log.info("Registered plugin executor: type={}", executor.getType());
        }
    }

    public Map<String, Object> toolCall(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Node node = nodeState.node();
        String pluginType = resolvePluginType(node);
        
        PluginExecutor executor = pluginRegistry.get(pluginType);
        if (executor == null) {
            throw new UnsupportedOperationException("No plugin executor found for type: " + pluginType);
        }
        
        return executor.call(nodeState, inputs);
    }
    
    public TtsIntegration getTtsIntegration() {
        for (TtsIntegration ttsIntegration : smartTTSIntegration) {
            if (Objects.equals(ttsIntegration.source(), ttsSource)) {
                return ttsIntegration;
            }
        }
        throw new RuntimeException("TTS 源不存在");
    }
    
    private String resolvePluginType(Node node) {
        Map<String, Object> nodeParam = node.getData().getNodeParam();
        if (nodeParam == null) {
            return "aitools";
        }
        
        String pluginId = (String) nodeParam.get("pluginId");
        if (pluginId == null) {
            return "aitools";
        }
        
        if (pluginId.startsWith("tts@") || pluginId.startsWith("TTS")) {
            return "tts";
        }
        
        if (pluginId.startsWith("code@")) {
            return "code";
        }
        
        return "aitools";
    }
}

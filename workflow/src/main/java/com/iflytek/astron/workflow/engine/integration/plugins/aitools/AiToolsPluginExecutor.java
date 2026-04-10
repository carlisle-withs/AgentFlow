package com.iflytek.astron.workflow.engine.integration.plugins.aitools;

import com.iflytek.astron.workflow.engine.domain.NodeState;
import com.iflytek.astron.workflow.engine.integration.plugins.PluginExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AiToolsPluginExecutor implements PluginExecutor {

    @Autowired
    private AiToolsIntegration aiToolsIntegration;

    @Override
    public Map<String, Object> call(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        return aiToolsIntegration.call(nodeState, inputs);
    }

    @Override
    public String getType() {
        return "aitools";
    }
}
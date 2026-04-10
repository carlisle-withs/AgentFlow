package com.iflytek.astron.workflow.engine.integration.plugins;

import com.iflytek.astron.workflow.engine.domain.NodeState;

import java.util.Map;

public interface PluginExecutor {
    Map<String, Object> call(NodeState nodeState, Map<String, Object> inputs) throws Exception;
    
    String getType();
}
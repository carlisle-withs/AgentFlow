package com.iflytek.astron.workflow.agent.service;

import com.iflytek.astron.workflow.agent.components.AgentMessage;

import java.util.List;

public interface AgentCommunicationService {

    void sendMessage(AgentMessage message);

    AgentMessage sendRequestAndWait(String senderId, String senderRole, String targetId, 
                                     Object content, String workflowId, long timeoutMs);

    List<AgentMessage> getMessages(String agentId, int limit);

    List<AgentMessage> getPendingRequests(String agentId);

    void acknowledgeMessage(String messageId);

    void registerAgent(String agentId, String agentRole, String workflowId);

    void unregisterAgent(String agentId);

    List<String> getOnlineAgents(String workflowId);

    void broadcastMessage(AgentMessage message);

    void writeToBlackboard(String agentId, String key, Object value, String workflowId);

    Object readFromBlackboard(String key, String workflowId);

    void addDependency(String fromAgentId, String toAgentId, Integer dependencyType);

    void removeDependency(String fromAgentId, String toAgentId);

    List<String> getBlockingAgents(String agentId);

    void waitForDependencies(String agentId, long timeoutMs);

    void releaseDependencies(String agentId);
}
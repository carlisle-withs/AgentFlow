package com.iflytek.astron.workflow.agent.components;

import com.iflytek.astron.workflow.agent.enums.AgentStatus;
import com.iflytek.astron.workflow.agent.enums.DependencyType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AgentRuntime {
    private final AgentRegistry registry;
    private final MessageRouter messageRouter;
    private final SharedBlackboard blackboard;
    private final DeadlockPrevention deadlockPrevention;
    private final Map<String, AgentBarrier> activeBarriers;

    public AgentRuntime() {
        this.registry = new AgentRegistry();
        this.messageRouter = new MessageRouter(registry);
        this.blackboard = new SharedBlackboard("default");
        this.deadlockPrevention = new DeadlockPrevention();
        this.activeBarriers = new ConcurrentHashMap<>();
    }

    public void registerAgent(String agentId, String workflowId, String nodeId, 
                               String role, List<String> capabilities) {
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentId(agentId);
        agentInfo.setWorkflowId(workflowId);
        agentInfo.setNodeId(nodeId);
        agentInfo.setAgentRole(role);
        agentInfo.setCapabilities(capabilities);
        agentInfo.setStatus(AgentStatus.ONLINE);
        
        registry.register(agentInfo);
    }

    public void unregisterAgent(String agentId) {
        registry.unregister(agentId);
        deadlockPrevention.clearDependencies(agentId);
    }

    public void sendMessage(String senderId, String targetId, Object content) {
        AgentMessage message = AgentMessage.createRequest(senderId, null, targetId, content, null);
        messageRouter.send(message);
    }

    public void broadcast(String senderId, Object content, String workflowId) {
        AgentMessage message = AgentMessage.createBroadcast(senderId, null, content, workflowId);
        messageRouter.broadcast(message);
    }

    public AgentMessage receiveMessage(String agentId, long timeoutMs) throws InterruptedException {
        InBox inbox = registry.getInBox(agentId);
        if (inbox == null) {
            return null;
        }
        return inbox.poll(timeoutMs);
    }

    public void writeKnowledge(String key, Object value, String authorId) {
        blackboard.write(key, value, authorId);
    }

    public Object readKnowledge(String key) {
        return blackboard.read(key);
    }

    public List<KnowledgeEntry> queryKnowledge(String pattern) {
        return blackboard.query(pattern);
    }

    public AgentBarrier createBarrier(String barrierId, String workflowId, 
                                       int requiredCount, long timeoutMs) {
        AgentBarrier barrier = new AgentBarrier(barrierId, workflowId, requiredCount, timeoutMs);
        activeBarriers.put(barrierId, barrier);
        return barrier;
    }

    public void destroyBarrier(String barrierId) {
        activeBarriers.remove(barrierId);
    }

    public AgentBarrier getBarrier(String barrierId) {
        return activeBarriers.get(barrierId);
    }

    public void addDependency(String waiterId, String targetId, DependencyType type) {
        deadlockPrevention.addDependency(waiterId, targetId, type);
    }

    public void removeDependency(String waiterId, String targetId) {
        deadlockPrevention.removeDependency(waiterId, targetId);
    }

    public boolean checkDeadlock() {
        return deadlockPrevention.hasCircularDependency();
    }

    public void updateAgentStatus(String agentId, AgentStatus status) {
        registry.updateStatus(agentId, status);
    }

    public void updateHeartbeat(String agentId) {
        registry.updateHeartbeat(agentId);
    }

    public List<String> findAgentsByRole(String role) {
        return registry.findAgentsByRole(role);
    }

    public List<String> findAgentsByCapability(String capability) {
        return registry.findAgentsByCapability(capability);
    }

    public AgentRegistry getRegistry() {
        return registry;
    }

    public MessageRouter getMessageRouter() {
        return messageRouter;
    }

    public SharedBlackboard getBlackboard() {
        return blackboard;
    }

    public DeadlockPrevention getDeadlockPrevention() {
        return deadlockPrevention;
    }

    public void shutdown() {
        registry.clear();
        blackboard.clear();
        activeBarriers.clear();
        log.info("AgentRuntime shutdown completed");
    }
}

package com.iflytek.astron.workflow.agent.components;

import com.iflytek.astron.workflow.agent.enums.AgentStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class AgentRegistry {
    private final Map<String, AgentInfo> agents;
    private final Map<String, List<String>> roleToAgentMap;
    private final Map<String, InBox> agentInboxes;

    public AgentRegistry() {
        this.agents = new ConcurrentHashMap<>();
        this.roleToAgentMap = new ConcurrentHashMap<>();
        this.agentInboxes = new ConcurrentHashMap<>();
    }

    public void register(AgentInfo agentInfo) {
        agents.put(agentInfo.getAgentId(), agentInfo);
        agentInboxes.put(agentInfo.getAgentId(), new InBox(agentInfo.getAgentId()));
        
        if (agentInfo.getAgentRole() != null) {
            roleToAgentMap.computeIfAbsent(agentInfo.getAgentRole(), k -> new CopyOnWriteArrayList<>())
                    .add(agentInfo.getAgentId());
        }
        
        log.info("Agent registered: {} with role: {}", agentInfo.getAgentId(), agentInfo.getAgentRole());
    }

    public void unregister(String agentId) {
        AgentInfo agentInfo = agents.remove(agentId);
        if (agentInfo != null) {
            InBox inbox = agentInboxes.remove(agentId);
            if (inbox != null) {
                inbox.shutdown();
            }
            
            if (agentInfo.getAgentRole() != null) {
                List<String> agentsInRole = roleToAgentMap.get(agentInfo.getAgentRole());
                if (agentsInRole != null) {
                    agentsInRole.remove(agentId);
                }
            }
            
            log.info("Agent unregistered: {}", agentId);
        }
    }

    public AgentInfo getAgent(String agentId) {
        return agents.get(agentId);
    }

    public InBox getInBox(String agentId) {
        return agentInboxes.get(agentId);
    }

    public List<String> findAgentsByRole(String role) {
        List<String> result = roleToAgentMap.get(role);
        return result != null ? List.copyOf(result) : List.of();
    }

    public List<String> findAgentsByCapability(String capability) {
        return agents.values().stream()
                .filter(agent -> agent.hasCapability(capability))
                .map(AgentInfo::getAgentId)
                .toList();
    }

    public void updateStatus(String agentId, AgentStatus status) {
        AgentInfo agent = agents.get(agentId);
        if (agent != null) {
            agent.setStatus(status);
            log.debug("Agent {} status updated to {}", agentId, status);
        }
    }

    public void updateHeartbeat(String agentId) {
        AgentInfo agent = agents.get(agentId);
        if (agent != null) {
            agent.updateHeartbeat();
        }
    }

    public List<AgentInfo> getAllAgents() {
        return List.copyOf(agents.values());
    }

    public List<AgentInfo> getOnlineAgents() {
        return agents.values().stream()
                .filter(agent -> agent.getStatus() == AgentStatus.ONLINE)
                .toList();
    }

    public boolean isAgentOnline(String agentId) {
        AgentInfo agent = agents.get(agentId);
        return agent != null && agent.getStatus() == AgentStatus.ONLINE;
    }

    public void clear() {
        agents.clear();
        roleToAgentMap.clear();
        agentInboxes.values().forEach(InBox::shutdown);
        agentInboxes.clear();
    }
}

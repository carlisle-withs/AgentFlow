package com.iflytek.astron.workflow.agent.components;

import com.iflytek.astron.workflow.agent.enums.AgentStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentInfo {
    private String agentId;
    private String workflowId;
    private String nodeId;
    private String agentRole;
    private List<String> capabilities;
    private AgentStatus status;
    private String sessionId;
    private int memorySize;
    private int maxMemory;
    private LocalDateTime lastHeartbeat;

    public AgentInfo() {
        this.status = AgentStatus.ONLINE;
        this.memorySize = 0;
        this.maxMemory = 8000;
        this.lastHeartbeat = LocalDateTime.now();
    }

    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = LocalDateTime.now();
    }

    public boolean isOnline() {
        return status == AgentStatus.ONLINE;
    }

    public boolean isBusy() {
        return status == AgentStatus.BUSY;
    }

    public boolean canAcceptTask() {
        return status == AgentStatus.ONLINE;
    }
}

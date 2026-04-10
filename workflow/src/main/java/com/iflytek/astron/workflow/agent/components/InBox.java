package com.iflytek.astron.workflow.agent.components;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class InBox {
    private final String agentId;
    private final LinkedBlockingQueue<AgentMessage> messages;
    private final Map<String, PendingRequest> pendingRequests;
    private volatile boolean running = true;

    public InBox(String agentId) {
        this.agentId = agentId;
        this.messages = new LinkedBlockingQueue<>();
        this.pendingRequests = new ConcurrentHashMap<>();
    }

    public void put(AgentMessage message) {
        try {
            messages.put(message);
            log.debug("Agent {} received message: {}", agentId, message.getMessageId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Agent {} failed to put message: {}", agentId, e.getMessage());
        }
    }

    public AgentMessage take() throws InterruptedException {
        return messages.take();
    }

    public AgentMessage poll(long timeoutMs) throws InterruptedException {
        return messages.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public AgentMessage takeIf(AgentMessage message) {
        if (messages.remove(message)) {
            return message;
        }
        return null;
    }

    public void addPendingRequest(String requestId, PendingRequest request) {
        pendingRequests.put(requestId, request);
    }

    public PendingRequest getPendingRequest(String requestId) {
        return pendingRequests.get(requestId);
    }

    public PendingRequest removePendingRequest(String requestId) {
        return pendingRequests.remove(requestId);
    }

    public boolean hasPendingRequest(String requestId) {
        return pendingRequests.containsKey(requestId);
    }

    public void clearPendingRequests() {
        pendingRequests.clear();
    }

    public int pendingRequestCount() {
        return pendingRequests.size();
    }

    public Map<String, PendingRequest> getPendingRequests() {
        return pendingRequests;
    }

    public void shutdown() {
        running = false;
        messages.clear();
        pendingRequests.clear();
    }

    public boolean isRunning() {
        return running;
    }

    public String getAgentId() {
        return agentId;
    }

    public int messageCount() {
        return messages.size();
    }
}

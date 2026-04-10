package com.iflytek.astron.workflow.agent.components;

import lombok.Data;

import java.util.concurrent.CompletableFuture;

@Data
public class PendingRequest {
    private final String requestId;
    private final String targetId;
    private final Object content;
    private final long startTime;
    private final long timeoutMs;
    private final CompletableFuture<AgentMessage> future;
    private volatile boolean replied = false;

    public PendingRequest(String requestId, String targetId, Object content, long timeoutMs) {
        this.requestId = requestId;
        this.targetId = targetId;
        this.content = content;
        this.startTime = System.currentTimeMillis();
        this.timeoutMs = timeoutMs;
        this.future = new CompletableFuture<>();
    }

    public boolean isTimeout() {
        return System.currentTimeMillis() - startTime > timeoutMs;
    }

    public long remainingTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, timeoutMs - elapsed);
    }

    public void setReplied() {
        this.replied = true;
    }

    public boolean isReplied() {
        return replied;
    }
}

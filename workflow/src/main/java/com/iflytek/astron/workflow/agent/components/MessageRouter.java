package com.iflytek.astron.workflow.agent.components;

import com.iflytek.astron.workflow.agent.enums.MessageStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class MessageRouter {
    private final AgentRegistry agentRegistry;
    private final long defaultTimeoutMs;

    public MessageRouter(AgentRegistry agentRegistry) {
        this(agentRegistry, 30000L);
    }

    public MessageRouter(AgentRegistry agentRegistry, long defaultTimeoutMs) {
        this.agentRegistry = agentRegistry;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public void send(AgentMessage message) {
        if (message.isBroadcast()) {
            broadcast(message);
            return;
        }

        String targetId = message.getTargetId();
        InBox inbox = agentRegistry.getInBox(targetId);
        
        if (inbox == null) {
            log.warn("Target agent not found: {}", targetId);
            return;
        }

        inbox.put(message);
        log.debug("Message sent from {} to {}", message.getSenderId(), targetId);
    }

    public CompletableFuture<AgentMessage> sendAndWait(AgentMessage message, long timeoutMs) {
        CompletableFuture<AgentMessage> future = new CompletableFuture<>();
        String requestId = message.getMessageId();
        
        PendingRequest pendingRequest = new PendingRequest(
                requestId,
                message.getTargetId(),
                message.getContent(),
                timeoutMs
        );

        InBox senderInbox = agentRegistry.getInBox(message.getSenderId());
        if (senderInbox != null) {
            senderInbox.addPendingRequest(requestId, pendingRequest);
        }

        send(message);

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(timeoutMs);
                if (!future.isDone()) {
                    future.completeExceptionally(new RuntimeException("Message timeout"));
                    if (senderInbox != null) {
                        senderInbox.removePendingRequest(requestId);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public CompletableFuture<AgentMessage> sendAndWait(AgentMessage message) {
        return sendAndWait(message, defaultTimeoutMs);
    }

    public void reply(String requestId, String senderId, Object content) {
        PendingRequest pendingRequest = null;
        InBox senderInbox = agentRegistry.getInBox(senderId);
        
        if (senderInbox != null) {
            pendingRequest = senderInbox.removePendingRequest(requestId);
        }

        if (pendingRequest != null) {
            pendingRequest.getFuture().complete(
                    AgentMessage.createResponse(
                            "system",
                            null,
                            senderId,
                            content,
                            requestId
                    )
            );
        }
    }

    public void broadcast(AgentMessage message) {
        List<AgentInfo> onlineAgents = agentRegistry.getOnlineAgents();
        
        for (AgentInfo agent : onlineAgents) {
            if (!agent.getAgentId().equals(message.getSenderId())) {
                InBox inbox = agentRegistry.getInBox(agent.getAgentId());
                if (inbox != null) {
                    inbox.put(message);
                }
            }
        }
        
        log.debug("Broadcast from {} to {} agents", message.getSenderId(), onlineAgents.size());
    }

    public void replyToRequest(AgentMessage request, AgentMessage response) {
        if (request.isRequest()) {
            response.setRelatedRequestId(request.getMessageId());
            send(response);
        }
    }

    public AgentRegistry getAgentRegistry() {
        return agentRegistry;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }
}

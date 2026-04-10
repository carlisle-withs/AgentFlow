package com.iflytek.astron.workflow.agent.controller;

import com.iflytek.astron.workflow.agent.components.AgentMessage;
import com.iflytek.astron.workflow.agent.enums.MessageType;
import com.iflytek.astron.workflow.agent.service.AgentCommunicationService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentCommunicationController {

    private final AgentCommunicationService agentCommunicationService;

    public AgentCommunicationController(AgentCommunicationService agentCommunicationService) {
        this.agentCommunicationService = agentCommunicationService;
    }

    @PostMapping("/message/send")
    public void sendMessage(@RequestBody SendMessageRequest request) {
        AgentMessage message = new AgentMessage();
        message.setSenderId(request.getSenderId());
        message.setSenderRole(request.getSenderRole());
        message.setTargetId(request.getTargetId());
        message.setMessageType(MessageType.fromCode(request.getMessageType()));
        message.setContent(request.getContent());
        message.setWorkflowId(request.getWorkflowId());
        message.setPriority(request.getPriority() != null ? request.getPriority() : 5);

        agentCommunicationService.sendMessage(message);
    }

    @PostMapping("/message/request")
    public Object sendRequest(@RequestBody SendMessageRequest request) {
        long timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : 30000;
        AgentMessage response = agentCommunicationService.sendRequestAndWait(
                request.getSenderId(),
                request.getSenderRole(),
                request.getTargetId(),
                request.getContent(),
                request.getWorkflowId(),
                timeoutMs
        );
        return response.getContent();
    }

    @PostMapping("/message/broadcast")
    public void broadcast(@RequestBody BroadcastRequest request) {
        AgentMessage message = new AgentMessage();
        message.setSenderId(request.getSenderId());
        message.setSenderRole(request.getSenderRole());
        message.setContent(request.getContent());
        message.setWorkflowId(request.getWorkflowId());

        agentCommunicationService.broadcastMessage(message);
    }

    @GetMapping("/message/{agentId}")
    public List<AgentMessage> getMessages(@PathVariable String agentId, @RequestParam(defaultValue = "10") int limit) {
        return agentCommunicationService.getMessages(agentId, limit);
    }

    @GetMapping("/message/pending/{agentId}")
    public List<AgentMessage> getPendingRequests(@PathVariable String agentId) {
        return agentCommunicationService.getPendingRequests(agentId);
    }

    @PostMapping("/message/acknowledge")
    public void acknowledgeMessage(@RequestBody AcknowledgeRequest request) {
        agentCommunicationService.acknowledgeMessage(request.getMessageId());
    }

    @PostMapping("/register")
    public void registerAgent(@RequestBody RegisterRequest request) {
        agentCommunicationService.registerAgent(
                request.getAgentId(),
                request.getAgentRole(),
                request.getWorkflowId()
        );
    }

    @PostMapping("/unregister")
    public void unregisterAgent(@RequestBody UnregisterRequest request) {
        agentCommunicationService.unregisterAgent(request.getAgentId());
    }

    @GetMapping("/online/{workflowId}")
    public List<String> getOnlineAgents(@PathVariable String workflowId) {
        return agentCommunicationService.getOnlineAgents(workflowId);
    }

    @PostMapping("/blackboard/write")
    public void writeToBlackboard(@RequestBody BlackboardWriteRequest request) {
        agentCommunicationService.writeToBlackboard(
                request.getAgentId(),
                request.getKey(),
                request.getValue(),
                request.getWorkflowId()
        );
    }

    @GetMapping("/blackboard/{workflowId}/{key}")
    public Object readFromBlackboard(@PathVariable String workflowId, @PathVariable String key) {
        return agentCommunicationService.readFromBlackboard(key, workflowId);
    }

    @PostMapping("/dependency/add")
    public void addDependency(@RequestBody DependencyRequest request) {
        agentCommunicationService.addDependency(
                request.getFromAgentId(),
                request.getToAgentId(),
                request.getDependencyType()
        );
    }

    @PostMapping("/dependency/remove")
    public void removeDependency(@RequestBody DependencyRemoveRequest request) {
        agentCommunicationService.removeDependency(
                request.getFromAgentId(),
                request.getToAgentId()
        );
    }

    @GetMapping("/dependency/blocking/{agentId}")
    public List<String> getBlockingAgents(@PathVariable String agentId) {
        return agentCommunicationService.getBlockingAgents(agentId);
    }

    @PostMapping("/dependency/wait")
    public void waitForDependencies(@RequestBody WaitForDependenciesRequest request) {
        long timeoutMs = request.getTimeoutMs() != null ? request.getTimeoutMs() : 60000;
        agentCommunicationService.waitForDependencies(request.getAgentId(), timeoutMs);
    }

    @PostMapping("/dependency/release")
    public void releaseDependencies(@RequestBody ReleaseDependenciesRequest request) {
        agentCommunicationService.releaseDependencies(request.getAgentId());
    }

    @Data
    public static class SendMessageRequest {
        private String senderId;
        private String senderRole;
        private String targetId;
        private Integer messageType;
        private Object content;
        private String workflowId;
        private Integer priority;
        private Long timeoutMs;
    }

    @Data
    public static class BroadcastRequest {
        private String senderId;
        private String senderRole;
        private Object content;
        private String workflowId;
    }

    @Data
    public static class AcknowledgeRequest {
        private String messageId;
    }

    @Data
    public static class RegisterRequest {
        private String agentId;
        private String agentRole;
        private String workflowId;
    }

    @Data
    public static class UnregisterRequest {
        private String agentId;
    }

    @Data
    public static class BlackboardWriteRequest {
        private String agentId;
        private String key;
        private Object value;
        private String workflowId;
    }

    @Data
    public static class DependencyRequest {
        private String fromAgentId;
        private String toAgentId;
        private Integer dependencyType;
    }

    @Data
    public static class DependencyRemoveRequest {
        private String fromAgentId;
        private String toAgentId;
    }

    @Data
    public static class WaitForDependenciesRequest {
        private String agentId;
        private Long timeoutMs;
    }

    @Data
    public static class ReleaseDependenciesRequest {
        private String agentId;
    }
}
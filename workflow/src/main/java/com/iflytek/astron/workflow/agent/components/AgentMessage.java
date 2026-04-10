package com.iflytek.astron.workflow.agent.components;

import com.iflytek.astron.workflow.agent.enums.MessageType;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class AgentMessage {
    private String messageId;
    private String senderId;
    private String senderRole;
    private String targetId;
    private MessageType messageType;
    private Object content;
    private Map<String, Object> metadata;
    private String relatedRequestId;
    private String workflowId;
    private String chatId;
    private Integer priority;
    private long timestamp;

    public AgentMessage() {
        this.messageId = UUID.randomUUID().toString().replace("-", "");
        this.timestamp = System.currentTimeMillis();
        this.priority = 5;
    }

    public static AgentMessage createRequest(String senderId, String senderRole, String targetId, 
                                              Object content, String workflowId) {
        AgentMessage msg = new AgentMessage();
        msg.setSenderId(senderId);
        msg.setSenderRole(senderRole);
        msg.setTargetId(targetId);
        msg.setMessageType(MessageType.REQUEST);
        msg.setContent(content);
        msg.setWorkflowId(workflowId);
        return msg;
    }

    public static AgentMessage createResponse(String senderId, String senderRole, 
                                               String targetId, Object content, String relatedRequestId) {
        AgentMessage msg = new AgentMessage();
        msg.setSenderId(senderId);
        msg.setSenderRole(senderRole);
        msg.setTargetId(targetId);
        msg.setMessageType(MessageType.RESPONSE);
        msg.setContent(content);
        msg.setRelatedRequestId(relatedRequestId);
        return msg;
    }

    public static AgentMessage createBroadcast(String senderId, String senderRole, 
                                               Object content, String workflowId) {
        AgentMessage msg = new AgentMessage();
        msg.setSenderId(senderId);
        msg.setSenderRole(senderRole);
        msg.setTargetId(null);
        msg.setMessageType(MessageType.BROADCAST);
        msg.setContent(content);
        msg.setWorkflowId(workflowId);
        return msg;
    }

    public boolean isRequest() {
        return MessageType.REQUEST.equals(messageType);
    }

    public boolean isResponse() {
        return MessageType.RESPONSE.equals(messageType);
    }

    public boolean isBroadcast() {
        return MessageType.BROADCAST.equals(messageType);
    }
}

package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message")
public class AgentMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private String messageId;

    @TableField("sender_id")
    private String senderId;

    @TableField("sender_role")
    private String senderRole;

    @TableField("target_id")
    private String targetId;

    @TableField("message_type")
    private Integer messageType;

    @TableField("content")
    private String content;

    @TableField("metadata")
    private String metadata;

    @TableField("related_request_id")
    private String relatedRequestId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("chat_id")
    private String chatId;

    @TableField("status")
    private Integer status;

    @TableField("priority")
    private Integer priority;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField("replied_at")
    private LocalDateTime repliedAt;

    @TableField("create_at")
    private LocalDateTime createAt;
}

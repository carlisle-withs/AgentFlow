package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_communication_log")
public class AgentCommunicationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("log_id")
    private String logId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("session_id")
    private String sessionId;

    @TableField("agent_id")
    private String agentId;

    @TableField("event_type")
    private String eventType;

    @TableField("event_data")
    private String eventData;

    @TableField("related_message_id")
    private String relatedMessageId;

    @TableField("related_plan_id")
    private String relatedPlanId;

    @TableField("execution_time_ms")
    private Long executionTimeMs;

    @TableField("result_status")
    private Integer resultStatus;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_at")
    private LocalDateTime createAt;
}

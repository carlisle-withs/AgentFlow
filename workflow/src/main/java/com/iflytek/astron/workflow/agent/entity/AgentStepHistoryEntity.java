package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_step_history")
public class AgentStepHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("step_id")
    private String stepId;

    @TableField("plan_id")
    private String planId;

    @TableField("agent_id")
    private String agentId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("step_order")
    private Integer stepOrder;

    @TableField("step_description")
    private String stepDescription;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_params")
    private String toolParams;

    @TableField("step_status")
    private Integer stepStatus;

    @TableField("execution_type")
    private String executionType;

    @TableField("reasoning")
    private String reasoning;

    @TableField("output")
    private String output;

    @TableField("tool_result")
    private String toolResult;

    @TableField("error_message")
    private String errorMessage;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("execution_time_ms")
    private Long executionTimeMs;

    @TableField("create_at")
    private LocalDateTime createAt;
}

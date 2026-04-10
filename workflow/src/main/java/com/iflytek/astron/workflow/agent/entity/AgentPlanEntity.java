package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_plan")
public class AgentPlanEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("plan_id")
    private String planId;

    @TableField("agent_id")
    private String agentId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("task_description")
    private String taskDescription;

    @TableField("steps")
    private String steps;

    @TableField("current_step_index")
    private Integer currentStepIndex;

    @TableField("status")
    private Integer status;

    @TableField("failure_reason")
    private String failureReason;

    @TableField("result")
    private String result;

    @TableField("max_steps")
    private Integer maxSteps;

    @TableField("loop_detected")
    private Integer loopDetected;

    @TableField("memory_snapshot")
    private String memorySnapshot;

    @TableField("create_at")
    private LocalDateTime createAt;

    @TableField("update_at")
    private LocalDateTime updateAt;
}

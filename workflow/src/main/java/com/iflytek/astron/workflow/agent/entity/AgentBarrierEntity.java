package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_barrier")
public class AgentBarrierEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("barrier_id")
    private String barrierId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("session_id")
    private String sessionId;

    @TableField("barrier_name")
    private String barrierName;

    @TableField("required_count")
    private Integer requiredCount;

    @TableField("arrived_count")
    private Integer arrivedCount;

    @TableField("arrived_agents")
    private String arrivedAgents;

    @TableField("status")
    private Integer status;

    @TableField("timeout")
    private Integer timeout;

    @TableField("trigger_at")
    private LocalDateTime triggerAt;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("result")
    private String result;

    @TableField("create_at")
    private LocalDateTime createAt;

    @TableField("update_at")
    private LocalDateTime updateAt;
}

package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_registry")
public class AgentRegistryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("agent_id")
    private String agentId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("node_id")
    private String nodeId;

    @TableField("agent_role")
    private String agentRole;

    @TableField("capabilities")
    private String capabilities;

    @TableField("status")
    private Integer status;

    @TableField("session_id")
    private String sessionId;

    @TableField("memory_size")
    private Integer memorySize;

    @TableField("max_memory")
    private Integer maxMemory;

    @TableField("last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @TableField("create_at")
    private LocalDateTime createAt;

    @TableField("update_at")
    private LocalDateTime updateAt;
}

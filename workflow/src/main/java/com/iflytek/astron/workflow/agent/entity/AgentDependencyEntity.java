package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_dependency")
public class AgentDependencyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("session_id")
    private String sessionId;

    @TableField("waiter_id")
    private String waiterId;

    @TableField("waiter_role")
    private String waiterRole;

    @TableField("target_id")
    private String targetId;

    @TableField("target_role")
    private String targetRole;

    @TableField("dependency_type")
    private String dependencyType;

    @TableField("status")
    private Integer status;

    @TableField("request_id")
    private String requestId;

    @TableField("timeout")
    private Integer timeout;

    @TableField("create_at")
    private LocalDateTime createAt;

    @TableField("update_at")
    private LocalDateTime updateAt;
}

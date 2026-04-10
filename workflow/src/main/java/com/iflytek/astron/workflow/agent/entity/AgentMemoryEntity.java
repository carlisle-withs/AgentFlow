package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_memory")
public class AgentMemoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("memory_id")
    private String memoryId;

    @TableField("agent_id")
    private String agentId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("session_id")
    private String sessionId;

    @TableField("memory_type")
    private Integer memoryType;

    @TableField("content")
    private String content;

    @TableField("importance")
    private Integer importance;

    @TableField("is_key_memory")
    private Integer isKeyMemory;

    @TableField("compressed")
    private Integer compressed;

    @TableField("summary")
    private String summary;

    @TableField("related_step_id")
    private String relatedStepId;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("status")
    private Integer status;

    @TableField("create_at")
    private LocalDateTime createAt;
}

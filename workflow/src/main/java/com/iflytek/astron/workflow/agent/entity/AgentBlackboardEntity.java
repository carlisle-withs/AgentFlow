package com.iflytek.astron.workflow.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_blackboard")
public class AgentBlackboardEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("board_id")
    private String boardId;

    @TableField("workflow_id")
    private String workflowId;

    @TableField("session_id")
    private String sessionId;

    @TableField("knowledge_key")
    private String knowledgeKey;

    @TableField("knowledge_value")
    private String knowledgeValue;

    @TableField("author_id")
    private String authorId;

    @TableField("author_role")
    private String authorRole;

    @TableField("version")
    private Long version;

    @TableField("visibility")
    private Integer visibility;

    @TableField("allowed_readers")
    private String allowedReaders;

    @TableField("tags")
    private String tags;

    @TableField("score")
    private BigDecimal score;

    @TableField("status")
    private Integer status;

    @TableField("ttl")
    private Integer ttl;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField("create_at")
    private LocalDateTime createAt;

    @TableField("update_at")
    private LocalDateTime updateAt;
}

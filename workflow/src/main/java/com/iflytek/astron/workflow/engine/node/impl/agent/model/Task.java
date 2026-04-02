package com.iflytek.astron.workflow.engine.node.impl.agent.model;

import lombok.Data;

import java.util.Map;

/**
 * Agent 任务
 */
@Data
public class Task {

    /**
     * 任务ID
     */
    private String id;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 任务目标
     */
    private String goal;

    /**
     * 输入参数
     */
    private Map<String, Object> inputs;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 任务优先级
     */
    private int priority;

    /**
     * 创建时间
     */
    private long createdAt;

    public Task() {
        this.status = TaskStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
    }

    public Task(String description) {
        this();
        this.description = description;
    }

    public enum TaskStatus {
        PENDING,
        PLANNING,
        EXECUTING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}

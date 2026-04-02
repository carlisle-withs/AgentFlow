package com.iflytek.astron.workflow.engine.node.impl.agent.model;

import lombok.Data;

import java.util.Map;

/**
 * 记忆项
 */
@Data
public class MemoryItem {

    /**
     * 记忆类型 (reasoning/tool_call/observation/result/error)
     */
    private String type;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 执行步骤
     */
    private int step;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    public MemoryItem() {
        this.timestamp = System.currentTimeMillis();
    }

    public MemoryItem(String type, String content, int step) {
        this();
        this.type = type;
        this.content = content;
        this.step = step;
    }

    public MemoryItem(String type, String content, int step, Map<String, Object> metadata) {
        this();
        this.type = type;
        this.content = content;
        this.step = step;
        this.metadata = metadata;
    }

    /**
     * 是否是关键记忆
     */
    public boolean isKeyMemory() {
        return "result".equals(type) || "final_answer".equals(type);
    }

    /**
     * 是否可以被压缩
     */
    public boolean isCompressible() {
        return "reasoning".equals(type) || "observation".equals(type);
    }
}

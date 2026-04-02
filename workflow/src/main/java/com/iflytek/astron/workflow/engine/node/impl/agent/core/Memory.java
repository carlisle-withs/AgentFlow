package com.iflytek.astron.workflow.engine.node.impl.agent.core;

import com.iflytek.astron.workflow.engine.node.impl.agent.model.ExecutionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.MemoryItem;

import java.util.List;

/**
 * 记忆接口
 * 负责存储执行历史和上下文摘要
 */
public interface Memory {

    /**
     * 添加记忆
     *
     * @param item 记忆项
     */
    void add(MemoryItem item);

    /**
     * 添加执行结果
     *
     * @param result 执行结果
     */
    void addResult(ExecutionResult result);

    /**
     * 获取所有记忆
     *
     * @return 记忆列表
     */
    List<MemoryItem> getAll();

    /**
     * 获取压缩后的上下文
     *
     * @param maxTokens 最大token数
     * @return 压缩后的上下文字符串
     */
    String getCompressedContext(int maxTokens);

    /**
     * 获取最近的记忆
     *
     * @param count 数量
     * @return 记忆列表
     */
    List<MemoryItem> getRecent(int count);

    /**
     * 摘要旧记忆
     *
     * @param oldItems 需要摘要的记忆
     * @return 摘要内容
     */
    String summarize(List<MemoryItem> oldItems);

    /**
     * 清空记忆
     */
    void clear();

    /**
     * 获取记忆大小
     *
     * @return 记忆项数量
     */
    int size();

    /**
     * 是否需要压缩
     *
     * @param maxTokens 最大token数
     * @return 是否需要
     */
    boolean needsCompression(int maxTokens);

    /**
     * 执行压缩
     *
     * @param maxTokens 最大token数
     */
    void compress(int maxTokens);

    /**
     * 获取关键记忆（不可压缩）
     */
    List<MemoryItem> getKeyMemories();
}

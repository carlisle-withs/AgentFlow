package com.iflytek.astron.workflow.engine.node.impl.agent.impl;

import com.iflytek.astron.workflow.engine.node.impl.agent.core.Memory;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.ExecutionResult;
import com.iflytek.astron.workflow.engine.node.impl.agent.model.MemoryItem;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认记忆实现
 * 支持记忆存储、摘要管理和上下文压缩
 */
@Slf4j
public class DefaultMemory implements Memory {

    private final List<MemoryItem> memories;
    private final MemoryConfig config;

    public DefaultMemory() {
        this(new MemoryConfig());
    }

    public DefaultMemory(MemoryConfig config) {
        this.config = config;
        this.memories = new ArrayList<>();
    }

    @Override
    public void add(MemoryItem item) {
        if (item == null) return;
        memories.add(item);
        // 注意：压缩由外部（Agent主循环）控制，这里不做自动压缩
    }

    @Override
    public void addResult(ExecutionResult result) {
        if (result == null) return;

        MemoryItem item = result.toMemoryItem();
        item.setStep(result.getStep());
        add(item);
    }

    @Override
    public List<MemoryItem> getAll() {
        return new ArrayList<>(memories);
    }

    @Override
    public String getCompressedContext(int maxTokens) {
        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        int currentTokens = 0;

        // 先添加关键记忆
        List<MemoryItem> keyMemories = getKeyMemories();
        for (MemoryItem item : keyMemories) {
            String content = formatMemoryItem(item);
            int tokens = estimateTokens(content);
            if (currentTokens + tokens <= maxTokens) {
                context.append(content);
                currentTokens += tokens;
            }
        }

        // 从最新到最旧添加记忆
        for (int i = memories.size() - 1; i >= 0; i--) {
            MemoryItem item = memories.get(i);
            if (item.isKeyMemory()) continue; // 已添加

            String content = formatMemoryItem(item);
            int tokens = estimateTokens(content);

            if (currentTokens + tokens <= maxTokens) {
                context.append(content);
                currentTokens += tokens;
            } else if (!item.isCompressible()) {
                // 不可压缩的关键信息，尝试添加摘要
                String summary = summarize(List.of(item));
                context.append(summary);
                currentTokens += estimateTokens(summary);
                break;
            }
        }

        return context.toString();
    }

    @Override
    public List<MemoryItem> getRecent(int count) {
        if (memories.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }

        int size = Math.min(count, memories.size());
        return new ArrayList<>(memories.subList(memories.size() - size, memories.size()));
    }

    @Override
    public String summarize(List<MemoryItem> oldItems) {
        if (oldItems == null || oldItems.isEmpty()) {
            return "";
        }

        // 简单的摘要策略：
        // 1. 保留记忆数量信息
        // 2. 提取关键结果
        // 3. 压缩重复的推理过程

        StringBuilder summary = new StringBuilder();
        summary.append("[历史记忆摘要，共").append(oldItems.size()).append("条]\n");

        // 提取关键结果
        List<MemoryItem> keyResults = oldItems.stream()
                .filter(m -> "result".equals(m.getType()) || "final_answer".equals(m.getType()))
                .collect(Collectors.toList());

        if (!keyResults.isEmpty()) {
            summary.append("关键结果:\n");
            for (MemoryItem item : keyResults) {
                summary.append("- ").append(item.getContent()).append("\n");
            }
        }

        // 统计工具调用
        long toolCalls = oldItems.stream()
                .filter(m -> "tool_call".equals(m.getType()))
                .count();
        if (toolCalls > 0) {
            summary.append("工具调用次数: ").append(toolCalls).append("\n");
        }

        return summary.toString();
    }

    @Override
    public void clear() {
        memories.clear();
    }

    @Override
    public int size() {
        return memories.size();
    }

    @Override
    public boolean needsCompression(int maxTokens) {
        return estimateTotalTokens() > maxTokens;
    }

    @Override
    public void compress(int maxTokens) {
        if (memories.isEmpty()) return;

        log.info("Compressing memory from {} items", memories.size());

        // 分离关键记忆和可压缩记忆
        List<MemoryItem> keyMemories = getKeyMemories();
        List<MemoryItem> compressibleMemories = memories.stream()
                .filter(MemoryItem::isCompressible)
                .collect(Collectors.toList());

        // 摘要可压缩记忆
        if (!compressibleMemories.isEmpty()) {
            String summary = summarize(compressibleMemories);
            MemoryItem summaryItem = new MemoryItem("summary", summary, 0);
            summaryItem.setTimestamp(System.currentTimeMillis());

            // 保留关键记忆和摘要
            memories.clear();
            memories.addAll(keyMemories);
            memories.add(summaryItem);
        }

        log.info("Memory compressed to {} items", memories.size());
    }

    @Override
    public List<MemoryItem> getKeyMemories() {
        return memories.stream()
                .filter(MemoryItem::isKeyMemory)
                .collect(Collectors.toList());
    }

    /**
     * 格式化记忆项
     */
    private String formatMemoryItem(MemoryItem item) {
        return String.format("[%s] %s\n", item.getType().toUpperCase(), item.getContent());
    }

    /**
     * 估算 token 数（简单估算：按字符数/4）
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }

    /**
     * 估算总 token 数
     */
    private int estimateTotalTokens() {
        int total = 0;
        for (MemoryItem item : memories) {
            total += estimateTokens(item.getContent());
        }
        return total;
    }

    @Data
    public static class MemoryConfig {
        /**
         * 最大 token 数
         */
        private int maxTokens = 8000;

        /**
         * 是否自动压缩（默认false，由外部控制压缩时机）
         */
        private boolean autoCompress = false;

        /**
         * 压缩保留的关键记忆数
         */
        private int keepKeyMemories = 5;

        /**
         * 摘要最大长度
         */
        private int summaryMaxLength = 500;
    }
}

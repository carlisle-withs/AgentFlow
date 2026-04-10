package com.iflytek.astron.workflow.agent.components;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
public class SharedBlackboard {
    private final String boardId;
    private final Map<String, KnowledgeEntry> knowledge;
    private final Map<String, List<Consumer<KnowledgeEntry>>> subscribers;
    private final Map<String, List<KnowledgeEntry>> keyMemories;

    public SharedBlackboard(String boardId) {
        this.boardId = boardId;
        this.knowledge = new ConcurrentHashMap<>();
        this.subscribers = new ConcurrentHashMap<>();
        this.keyMemories = new ConcurrentHashMap<>();
    }

    public void write(String key, Object value, String authorId) {
        write(key, value, authorId, null);
    }

    public void write(String key, Object value, String authorId, String authorRole) {
        KnowledgeEntry entry = knowledge.computeIfAbsent(key, k -> new KnowledgeEntry());
        entry.setKey(key);
        entry.setValue(value);
        entry.setAuthorId(authorId);
        entry.setAuthorRole(authorRole);
        entry.setVersion(entry.getVersion() + 1);
        entry.setCreateAt(LocalDateTime.now());

        notifySubscribers(key, entry);
        log.debug("Blackboard write: {} by {}", key, authorId);
    }

    public Object read(String key) {
        KnowledgeEntry entry = knowledge.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.getValue();
    }

    public KnowledgeEntry readEntry(String key) {
        KnowledgeEntry entry = knowledge.get(key);
        if (entry != null && entry.isExpired()) {
            knowledge.remove(key);
            return null;
        }
        return entry;
    }

    public List<KnowledgeEntry> query(String pattern) {
        List<KnowledgeEntry> results = new ArrayList<>();
        String normalizedPattern = pattern.toLowerCase();
        
        for (KnowledgeEntry entry : knowledge.values()) {
            if (!entry.isExpired() && 
                (entry.getKey().toLowerCase().contains(normalizedPattern) ||
                 (entry.getTags() != null && entry.getTags().toLowerCase().contains(normalizedPattern)))) {
                results.add(entry);
            }
        }
        
        return results;
    }

    public List<KnowledgeEntry> queryByTags(String tags) {
        List<KnowledgeEntry> results = new ArrayList<>();
        String normalizedTags = tags.toLowerCase();
        
        for (KnowledgeEntry entry : knowledge.values()) {
            if (!entry.isExpired() && 
                entry.getTags() != null && 
                entry.getTags().toLowerCase().contains(normalizedTags)) {
                results.add(entry);
            }
        }
        
        return results;
    }

    public void subscribe(String agentId, String keyPattern, Consumer<KnowledgeEntry> callback) {
        String subscriberKey = agentId + ":" + keyPattern;
        subscribers.computeIfAbsent(keyPattern, k -> new ArrayList<>()).add(callback);
        log.debug("Agent {} subscribed to pattern: {}", agentId, keyPattern);
    }

    public void unsubscribe(String agentId, String keyPattern) {
        String subscriberKey = agentId + ":" + keyPattern;
        List<Consumer<KnowledgeEntry>> callbacks = subscribers.get(keyPattern);
        if (callbacks != null) {
            callbacks.removeIf(cb -> cb.toString().contains(agentId));
        }
    }

    private void notifySubscribers(String key, KnowledgeEntry entry) {
        for (Map.Entry<String, List<Consumer<KnowledgeEntry>>> subscriber : subscribers.entrySet()) {
            String pattern = subscriber.getKey();
            if (key.toLowerCase().contains(pattern.toLowerCase())) {
                for (Consumer<KnowledgeEntry> callback : subscriber.getValue()) {
                    try {
                        callback.accept(entry);
                    } catch (Exception e) {
                        log.error("Error notifying subscriber for key {}: {}", key, e.getMessage());
                    }
                }
            }
        }
    }

    public void addKeyMemory(String agentId, KnowledgeEntry entry) {
        keyMemories.computeIfAbsent(agentId, k -> new ArrayList<>()).add(entry);
    }

    public List<KnowledgeEntry> getKeyMemories(String agentId) {
        return keyMemories.getOrDefault(agentId, new ArrayList<>());
    }

    public void clear() {
        knowledge.clear();
        subscribers.clear();
        keyMemories.clear();
    }

    public void clearExpired() {
        knowledge.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public String getBoardId() {
        return boardId;
    }

    public int size() {
        return knowledge.size();
    }
}

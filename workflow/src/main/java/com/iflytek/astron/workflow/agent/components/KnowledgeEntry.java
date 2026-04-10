package com.iflytek.astron.workflow.agent.components;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class KnowledgeEntry {
    private String key;
    private Object value;
    private String authorId;
    private String authorRole;
    private long version;
    private int visibility;
    private String allowedReaders;
    private String tags;
    private BigDecimal score;
    private int status;
    private int ttl;
    private LocalDateTime expireAt;
    private LocalDateTime createAt;

    public boolean isExpired() {
        if (expireAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expireAt);
    }

    public boolean isVisibleTo(String agentId) {
        if (visibility == 0) {
            return true;
        }
        if (visibility == 2) {
            return authorId.equals(agentId);
        }
        return allowedReaders != null && allowedReaders.contains(agentId);
    }

    public void incrementVersion() {
        this.version++;
    }
}

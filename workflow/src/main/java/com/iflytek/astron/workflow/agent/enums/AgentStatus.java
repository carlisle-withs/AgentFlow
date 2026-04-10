package com.iflytek.astron.workflow.agent.enums;

import lombok.Getter;

@Getter
public enum AgentStatus {
    OFFLINE(0, "离线"),
    ONLINE(1, "在线"),
    BUSY(2, "忙碌"),
    EXCEPTION(3, "异常");

    private final int code;
    private final String description;

    AgentStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AgentStatus fromCode(int code) {
        for (AgentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}

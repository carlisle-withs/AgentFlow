package com.iflytek.astron.workflow.agent.enums;

import lombok.Getter;

@Getter
public enum BarrierStatus {
    WAITING(0, "等待中"),
    TRIGGERED(1, "已触发"),
    TIMEOUT(2, "超时"),
    CANCELLED(3, "取消");

    private final int code;
    private final String description;

    BarrierStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static BarrierStatus fromCode(int code) {
        for (BarrierStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}

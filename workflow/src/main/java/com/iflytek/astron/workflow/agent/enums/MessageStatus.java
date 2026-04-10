package com.iflytek.astron.workflow.agent.enums;

import lombok.Getter;

@Getter
public enum MessageStatus {
    PENDING(0, "待发送"),
    SENT(1, "已发送"),
    READ(2, "已读"),
    REPLIED(3, "已回复"),
    FAILED(4, "失败"),
    EXPIRED(5, "已过期");

    private final int code;
    private final String description;

    MessageStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static MessageStatus fromCode(int code) {
        for (MessageStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}

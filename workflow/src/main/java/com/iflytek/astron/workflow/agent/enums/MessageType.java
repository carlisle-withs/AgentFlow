package com.iflytek.astron.workflow.agent.enums;

import lombok.Getter;

@Getter
public enum MessageType {
    REQUEST(1, "请求"),
    RESPONSE(2, "响应"),
    BROADCAST(3, "广播"),
    NOTIFY(4, "通知"),
    QUERY(5, "查询"),
    UPDATE(6, "更新");

    private final int code;
    private final String description;

    MessageType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}

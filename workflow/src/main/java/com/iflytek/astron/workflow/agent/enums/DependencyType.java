package com.iflytek.astron.workflow.agent.enums;

import lombok.Getter;

@Getter
public enum DependencyType {
    MESSAGE("MESSAGE", "消息依赖"),
    TOOL("TOOL", "工具依赖"),
    RESULT("RESULT", "结果依赖"),
    BARRIER("BARRIER", "屏障依赖");

    private final String code;
    private final String description;

    DependencyType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static DependencyType fromCode(String code) {
        for (DependencyType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}

package com.iflytek.astron.workflow.agent.enums;

import lombok.Getter;

@Getter
public enum MemoryType {
    REASONING(1, "推理记忆"),
    OBSERVATION(2, "观察记忆"),
    RESULT(3, "结果记忆"),
    METACOGNITION(4, "元认知");

    private final int code;
    private final String description;

    MemoryType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static MemoryType fromCode(int code) {
        for (MemoryType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}

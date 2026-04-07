package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 响应动作解析器
 * 负责解析 LLM 输出，提取动作类型和参数
 */
@Slf4j
public class ActionParser {

    /**
     * 工具调用正则 (格式: <tool_call>{"name": "xxx", "arguments": {...}}</tool_call>)
     */
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
            Pattern.DOTALL
    );

    /**
     * 最终答案标记
     */
    private static final String FINAL_ANSWER_PREFIX = "<final_answer>";

    /**
     * 思考内容标记
     */
    private static final Pattern THINKING_PATTERN = Pattern.compile(
            "<thinking>\\s*(.*?)\\s*</thinking>",
            Pattern.DOTALL
    );

    /**
     * 解析 LLM 输出
     *
     * @param llmOutput LLM 原始输出
     * @return 解析后的动作
     */
    public static Action parse(String llmOutput) {
        Action action = new Action();

        if (llmOutput == null || llmOutput.isEmpty()) {
            action.setType(ActionType.ERROR);
            action.setErrorMessage("LLM output is empty");
            return action;
        }

        // 1. 提取思考内容
        Matcher thinkingMatcher = THINKING_PATTERN.matcher(llmOutput);
        if (thinkingMatcher.find()) {
            action.setThinking(thinkingMatcher.group(1).trim());
        }

        // 2. 检查是否是最终答案
        int finalAnswerIndex = llmOutput.indexOf(FINAL_ANSWER_PREFIX);
        if (finalAnswerIndex != -1) {
            action.setType(ActionType.FINAL_ANSWER);
            String answerContent = llmOutput.substring(finalAnswerIndex + FINAL_ANSWER_PREFIX.length()).trim();
            // 清理可能的后续标签
            int tagIndex = answerContent.indexOf('<');
            if (tagIndex > 0) {
                answerContent = answerContent.substring(0, tagIndex).trim();
            }
            action.setContent(answerContent);
            return action;
        }

        // 3. 检查是否是工具调用
        Matcher toolMatcher = TOOL_CALL_PATTERN.matcher(llmOutput);
        if (toolMatcher.find()) {
            String toolCallJson = null;
            try {
                toolCallJson = toolMatcher.group(1);
                JSONObject toolCallObj = JSON.parseObject(toolCallJson);

                action.setType(ActionType.TOOL_CALL);
                action.setToolName(toolCallObj.getString("name"));
                action.setToolArguments(toolCallObj.getJSONObject("arguments"));

                // 提取工具调用的原始文本（包含思考过程）
                String rawText = llmOutput.substring(0, toolMatcher.end());
                action.setRawText(rawText);

                return action;
            } catch (Exception e) {
                log.error("Failed to parse tool call: {}", toolCallJson != null ? toolCallJson : "null", e);
                action.setType(ActionType.ERROR);
                action.setErrorMessage("Failed to parse tool call: " + e.getMessage());
                return action;
            }
        }

        // 4. 无法识别动作类型，视为最终答案或错误
        // 如果没有明显标记，检查是否包含工具名称作为关键字
        for (String keyword : new String[]{"answer", "result", "结论", "结果", "答案"}) {
            if (llmOutput.toLowerCase().contains(keyword)) {
                action.setType(ActionType.FINAL_ANSWER);
                action.setContent(extractCleanContent(llmOutput));
                return action;
            }
        }

        // 默认视为继续推理
        action.setType(ActionType.CONTINUE);
        action.setContent(llmOutput);
        return action;
    }

    /**
     * 提取干净的内容（移除 XML 标签）
     */
    private static String extractCleanContent(String text) {
        if (text == null) return "";

        // 移除 thinking 标签
        text = text.replaceAll("<thinking>.*?</thinking>", "");
        // 移除 tool_call 标签
        text = text.replaceAll("<tool_call>.*?</tool_call>", "");
        // 移除 final_answer 标签
        text = text.replaceAll("<final_answer>", "");
        // 清理多余空白
        text = text.replaceAll("\\s+", " ").trim();

        return text;
    }

    /**
     * 判断是否包含特定工具调用
     */
    public static boolean containsToolCall(String llmOutput, String toolName) {
        if (llmOutput == null || toolName == null) return false;
        return llmOutput.contains("<tool_call>") && llmOutput.contains("\"name\":" ) && llmOutput.contains(toolName);
    }

    /**
     * 动作类型枚举
     */
    public enum ActionType {
        /**
         * 最终答案
         */
        FINAL_ANSWER,

        /**
         * 工具调用
         */
        TOOL_CALL,

        /**
         * 继续推理
         */
        CONTINUE,

        /**
         * 错误
         */
        ERROR
    }

    /**
     * 动作数据结构
     */
    @Data
    public static class Action {
        /**
         * 动作类型
         */
        private ActionType type;

        /**
         * 思考内容
         */
        private String thinking;

        /**
         * 动作内容（最终答案或推理内容）
         */
        private String content;

        /**
         * 工具名称（工具调用时）
         */
        private String toolName;

        /**
         * 工具参数（工具调用时）
         */
        private JSONObject toolArguments;

        /**
         * 原始文本
         */
        private String rawText;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 是否为最终答案
         */
        public boolean isFinalAnswer() {
            return type == ActionType.FINAL_ANSWER;
        }

        /**
         * 是否为工具调用
         */
        public boolean isToolCall() {
            return type == ActionType.TOOL_CALL;
        }

        /**
         * 是否有错误
         */
        public boolean isError() {
            return type == ActionType.ERROR;
        }
    }
}

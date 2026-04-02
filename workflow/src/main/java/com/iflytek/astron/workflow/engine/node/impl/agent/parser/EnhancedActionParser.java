package com.iflytek.astron.workflow.engine.node.impl.agent.parser;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增强的动作解析器
 * 支持多种解析策略，提高解析成功率
 */
@Slf4j
public class EnhancedActionParser {

    /**
     * 解析策略
     */
    public enum ParseStrategy {
        XML,           // XML标签解析
        REGEX,         // 正则表达式提取
        JSON,          // JSON格式解析
        LLM_CORRECTION // LLM自我修正（需要外部LLM调用）
    }

    /**
     * 解析结果
     */
    @Data
    public static class ParseResult {
        private boolean success;
        private Action action;
        private String usedStrategy;
        private String error;
        private int confidence;  // 0-100

        public static ParseResult ok(Action action, String strategy, int confidence) {
            ParseResult result = new ParseResult();
            result.success = true;
            result.action = action;
            result.usedStrategy = strategy;
            result.confidence = confidence;
            return result;
        }

        public static ParseResult fail(String error) {
            ParseResult result = new ParseResult();
            result.success = false;
            result.error = error;
            result.confidence = 0;
            return result;
        }
    }

    /**
     * 动作数据结构
     */
    @Data
    public static class Action {
        private ActionType type;
        private String thinking;
        private String content;
        private String toolName;
        private Map<String, Object> toolArguments;
        private String rawText;
        private String errorMessage;
        private List<ToolCall> toolCalls;  // 支持多个工具调用

        public boolean isFinalAnswer() {
            return type == ActionType.FINAL_ANSWER;
        }

        public boolean isToolCall() {
            return type == ActionType.TOOL_CALL;
        }

        public boolean isError() {
            return type == ActionType.ERROR;
        }

        public boolean isContinue() {
            return type == ActionType.CONTINUE;
        }

        public enum ActionType {
            FINAL_ANSWER,
            TOOL_CALL,
            CONTINUE,
            ERROR
        }
    }

    /**
     * 工具调用
     */
    @Data
    public static class ToolCall {
        private String name;
        private Map<String, Object> arguments;
    }

    private final List<ParseStrategy> strategies;

    public EnhancedActionParser() {
        this.strategies = List.of(ParseStrategy.XML, ParseStrategy.REGEX, ParseStrategy.JSON);
    }

    public EnhancedActionParser(List<ParseStrategy> strategies) {
        this.strategies = strategies != null ? strategies : List.of(ParseStrategy.XML);
    }

    /**
     * 解析 LLM 输出
     *
     * @param llmOutput LLM 原始输出
     * @return 解析结果
     */
    public ParseResult parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isEmpty()) {
            return ParseResult.fail("Empty LLM output");
        }

        // 尝试各种策略
        for (ParseStrategy strategy : strategies) {
            try {
                Action action = tryParse(llmOutput, strategy);
                if (action != null) {
                    int confidence = calculateConfidence(action, strategy);
                    return ParseResult.ok(action, strategy.name(), confidence);
                }
            } catch (Exception e) {
                log.debug("Strategy {} failed: {}", strategy, e.getMessage());
            }
        }

        // 所有策略都失败，返回错误
        return ParseResult.fail("All parsing strategies failed");
    }

    /**
     * 尝试使用指定策略解析
     */
    private Action tryParse(String llmOutput, ParseStrategy strategy) {
        switch (strategy) {
            case XML:
                return parseXml(llmOutput);
            case REGEX:
                return parseRegex(llmOutput);
            case JSON:
                return parseJson(llmOutput);
            case LLM_CORRECTION:
                // 需要外部LLM调用，暂时不支持
                return null;
            default:
                return null;
        }
    }

    /**
     * XML 标签解析
     */
    private Action parseXml(String output) {
        Action action = new Action();

        // 提取 thinking
        String thinking = extractXmlContent(output, "thinking");
        action.setThinking(thinking);

        // 检查 final_answer
        if (output.contains("<final_answer>")) {
            action.setType(Action.ActionType.FINAL_ANSWER);
            action.setContent(extractFinalAnswer(output));
            return action;
        }

        // 检查 tool_call
        if (output.contains("<tool_call>")) {
            // 支持多个 tool_call 标签
            List<ToolCall> toolCalls = extractMultipleToolCalls(output);
            if (!toolCalls.isEmpty()) {
                action.setType(Action.ActionType.TOOL_CALL);
                action.setToolCalls(toolCalls);
                action.setToolName(toolCalls.get(0).getName());
                action.setToolArguments(toolCalls.get(0).getArguments());

                // 设置原始文本
                int start = output.indexOf("<tool_call>");
                int end = output.lastIndexOf("</tool_call>") + 11;
                if (start >= 0 && end > start) {
                    action.setRawText(output.substring(0, Math.min(end, output.length())));
                }

                return action;
            }
        }

        // 检查 tool_calls（复数形式）
        if (output.contains("<tool_calls>")) {
            List<ToolCall> toolCalls = extractToolCallsFromArray(output);
            if (!toolCalls.isEmpty()) {
                action.setType(Action.ActionType.TOOL_CALL);
                action.setToolCalls(toolCalls);
                action.setToolName(toolCalls.get(0).getName());
                action.setToolArguments(toolCalls.get(0).getArguments());
                return action;
            }
        }

        // 无法识别，返回继续推理
        action.setType(Action.ActionType.CONTINUE);
        action.setContent(output);
        return action;
    }

    /**
     * 正则表达式解析
     */
    private Action parseRegex(String output) {
        Action action = new Action();

        // 提取 thinking
        Pattern thinkingPattern = Pattern.compile(
                "<thinking>\\s*(.*?)\\s*</thinking>",
                Pattern.DOTALL
        );
        Matcher thinkingMatcher = thinkingPattern.matcher(output);
        if (thinkingMatcher.find()) {
            action.setThinking(thinkingMatcher.group(1).trim());
        }

        // 提取 final_answer
        Pattern finalPattern = Pattern.compile(
                "<final_answer>\\s*(.*?)\\s*(?:</final_answer>|$)",
                Pattern.DOTALL
        );
        Matcher finalMatcher = finalPattern.matcher(output);
        if (finalMatcher.find()) {
            action.setType(Action.ActionType.FINAL_ANSWER);
            action.setContent(finalMatcher.group(1).trim());
            return action;
        }

        // 提取 tool_call
        Pattern toolPattern = Pattern.compile(
                "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
                Pattern.DOTALL
        );
        Matcher toolMatcher = toolPattern.matcher(output);
        if (toolMatcher.find()) {
            try {
                String toolCallJson = toolMatcher.group(1);
                JSONObject toolObj = JSON.parseObject(toolCallJson);

                ToolCall toolCall = new ToolCall();
                toolCall.setName(toolObj.getString("name"));
                toolCall.setArguments(toolObj.getJSONObject("arguments"));

                action.setType(Action.ActionType.TOOL_CALL);
                action.setToolName(toolCall.getName());
                action.setToolArguments(toolCall.getArguments());
                action.setToolCalls(List.of(toolCall));
                action.setRawText(toolMatcher.group(0));

                return action;
            } catch (Exception e) {
                log.debug("Failed to parse tool call JSON: {}", e.getMessage());
            }
        }

        // 无法识别
        action.setType(Action.ActionType.CONTINUE);
        action.setContent(output);
        return action;
    }

    /**
     * JSON 格式解析
     */
    private Action parseJson(String output) {
        // 尝试提取 JSON 对象
        String jsonStr = extractJsonObject(output);
        if (jsonStr != null) {
            try {
                JSONObject json = JSON.parseObject(jsonStr);

                Action action = new Action();

                // 检查类型
                String type = json.getString("type");
                if ("final_answer".equalsIgnoreCase(type)) {
                    action.setType(Action.ActionType.FINAL_ANSWER);
                    action.setContent(json.getString("content"));
                    return action;
                }

                if ("tool_call".equalsIgnoreCase(type) || "toolcall".equalsIgnoreCase(type)) {
                    ToolCall toolCall = new ToolCall();
                    toolCall.setName(json.getString("name"));

                    if (json.containsKey("arguments")) {
                        toolCall.setArguments(json.getJSONObject("arguments"));
                    }

                    action.setType(Action.ActionType.TOOL_CALL);
                    action.setToolName(toolCall.getName());
                    action.setToolArguments(toolCall.getArguments());
                    action.setToolCalls(List.of(toolCall));
                    return action;
                }

            } catch (Exception e) {
                log.debug("JSON parsing failed: {}", e.getMessage());
            }
        }

        // 尝试提取 JSON 数组（多个工具调用）
        String jsonArrayStr = extractJsonArray(output);
        if (jsonArrayStr != null) {
            try {
                JSONArray jsonArray = JSON.parseArray(jsonArrayStr);
                List<ToolCall> toolCalls = new ArrayList<>();

                for (int i = 0; i < jsonArray.size(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    ToolCall toolCall = new ToolCall();
                    toolCall.setName(item.getString("name"));
                    toolCall.setArguments(item.getJSONObject("arguments"));
                    toolCalls.add(toolCall);
                }

                if (!toolCalls.isEmpty()) {
                    Action action = new Action();
                    action.setType(Action.ActionType.TOOL_CALL);
                    action.setToolCalls(toolCalls);
                    action.setToolName(toolCalls.get(0).getName());
                    action.setToolArguments(toolCalls.get(0).getArguments());
                    return action;
                }

            } catch (Exception e) {
                log.debug("JSON array parsing failed: {}", e.getMessage());
            }
        }

        return null; // JSON解析失败
    }

    private String extractXmlContent(String text, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";

        int start = text.indexOf(startTag);
        int end = text.indexOf(endTag);

        if (start != -1 && end != -1 && end > start) {
            return text.substring(start + startTag.length(), end).trim();
        }
        return null;
    }

    private String extractFinalAnswer(String text) {
        String answer = extractXmlContent(text, "final_answer");
        if (answer != null) return answer;

        // 尝试直接提取
        int start = text.indexOf("<final_answer>");
        if (start != -1) {
            return text.substring(start + 14).replaceAll("<.*", "").trim();
        }
        return text;
    }

    private List<ToolCall> extractMultipleToolCalls(String text) {
        List<ToolCall> toolCalls = new ArrayList<>();
        String startTag = "<tool_call>";
        String endTag = "</tool_call>";

        int searchFrom = 0;
        while (true) {
            int start = text.indexOf(startTag, searchFrom);
            if (start == -1) break;

            int end = text.indexOf(endTag, start);
            if (end == -1) break;

            String toolCallXml = text.substring(start + startTag.length(), end).trim();
            try {
                JSONObject toolObj = JSON.parseObject(toolCallXml);
                ToolCall toolCall = new ToolCall();
                toolCall.setName(toolObj.getString("name"));
                toolCall.setArguments(toolObj.getJSONObject("arguments"));
                toolCalls.add(toolCall);
            } catch (Exception e) {
                log.debug("Failed to parse tool call: {}", e.getMessage());
            }

            searchFrom = end + endTag.length();
        }

        return toolCalls;
    }

    private List<ToolCall> extractToolCallsFromArray(String text) {
        List<ToolCall> toolCalls = new ArrayList<>();

        String arrayStr = extractJsonArray(text);
        if (arrayStr == null) return toolCalls;

        try {
            JSONArray jsonArray = JSON.parseArray(arrayStr);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                ToolCall toolCall = new ToolCall();
                toolCall.setName(item.getString("name"));
                toolCall.setArguments(item.getJSONObject("arguments"));
                toolCalls.add(toolCall);
            }
        } catch (Exception e) {
            log.debug("Failed to parse tool calls array: {}", e.getMessage());
        }

        return toolCalls;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start != -1 && end != -1 && end > start) {
            String jsonStr = text.substring(start, end + 1);
            // 验证是否是有效的JSON
            try {
                JSON.parseObject(jsonStr);
                return jsonStr;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');

        if (start != -1 && end != -1 && end > start) {
            String jsonStr = text.substring(start, end + 1);
            try {
                JSON.parseArray(jsonStr);
                return jsonStr;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 计算置信度
     */
    private int calculateConfidence(Action action, ParseStrategy strategy) {
        int confidence = 50; // 基础置信度

        // XML策略有明确标签，置信度较高
        if (strategy == ParseStrategy.XML) {
            confidence += 30;
        }

        // 有thinking内容
        if (action.getThinking() != null && !action.getThinking().isEmpty()) {
            confidence += 10;
        }

        // 工具调用参数完整
        if (action.isToolCall() && action.getToolArguments() != null) {
            confidence += 10;
        }

        return Math.min(confidence, 100);
    }

    /**
     * 生成修正提示
     * 当解析失败时，用于让LLM重新输出正确格式
     */
    public static String generateCorrectionPrompt(String failedOutput) {
        return String.format(
                "上一步输出格式错误，无法解析。请严格按照以下格式输出：\n\n" +
                        "1. 如果需要工具辅助完成任务：\n" +
                        "<thinking>你的推理过程...</thinking>\n" +
                        "<tool_call>{\"name\": \"工具名称\", \"arguments\": {\"参数\": \"值\"}}</tool_call>\n\n" +
                        "2. 如果任务已完成：\n" +
                        "<thinking>你的推理过程...</thinking>\n" +
                        "<final_answer>最终答案内容</final_answer>\n\n" +
                        "错误内容：\n%s",
                failedOutput
        );
    }
}

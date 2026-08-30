package com.chandler.learning.agent.ai.gateway.parser;

import java.util.List;

/**
 * 结构化输出无法恢复时的技术诊断异常。
 * <p>
 * 服务层会将其转换为稳定的业务错误码，同时把有限诊断信息记入 AI 调用审计。
 */
public class AiStructuredResponseParseException extends RuntimeException {

    private final String parserName;
    private final String parseStage;
    private final List<String> repairs;

    public AiStructuredResponseParseException(String parserName, String parseStage, List<String> repairs,
                                              Throwable cause) {
        super("结构化响应解析失败", cause);
        this.parserName = parserName;
        this.parseStage = parseStage;
        this.repairs = repairs == null ? List.of() : List.copyOf(repairs);
    }

    /** 返回发生失败的响应解析器名称。 */
    public String getParserName() {
        return parserName;
    }

    /** 返回响应解析失败的处理阶段。 */
    public String getParseStage() {
        return parseStage;
    }

    /** 返回失败前已尝试的兼容修复动作。 */
    public List<String> getRepairs() {
        return repairs;
    }
}

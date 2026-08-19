package com.chandler.learning.agent.support;

import com.chandler.learning.agent.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.domain.enums.AiResponseParserType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型输出解析器路由表。
 * <p>
 * 精确的供应商/模型解析器优先，最后才回退通用严格解析器，避免模型兼容规则散落在业务服务中。
 */
@Component
public class AiStructuredResponseParserRegistry {

    private final List<AiStructuredResponseParser> parsers;

    public AiStructuredResponseParserRegistry(ObjectMapper objectMapper) {
        this.parsers = List.of(
                new KimiJsonResponseParser(objectMapper),
                new DeepSeekJsonResponseParser(objectMapper),
                new StrictJsonResponseParser(objectMapper));
    }

    /**
     * 解析指定模型的结构化响应。场景参数保留为明确的调用边界，字段契约由调用方随后校验。
     */
    public AiStructuredResponseParseResult parse(AiInvocationScene invocationScene,
                                                  AiResponseParserType parserType, String content) {
        Class<? extends AiStructuredResponseParser> parserClass = switch (parserType) {
            case DEEPSEEK_JSON -> DeepSeekJsonResponseParser.class;
            case KIMI_JSON -> KimiJsonResponseParser.class;
            case STRICT_JSON -> StrictJsonResponseParser.class;
        };
        return parsers.stream()
                .filter(parserClass::isInstance)
                .findFirst()
                .orElseThrow()
                .parse(content);
    }
}

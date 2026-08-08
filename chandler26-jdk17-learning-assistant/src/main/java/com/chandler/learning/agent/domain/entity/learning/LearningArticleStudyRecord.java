package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import com.chandler.learning.agent.support.LearningConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章学习记录 DO。
 * <p>
 * 保存用户基于单词本词汇生成的文章学习材料，支持同一批词汇重复学习和缓存复用。
 */
@Data
@TableName("learning_article_study_record")
@Schema(name = "文章学习记录")
public class LearningArticleStudyRecord extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 记录所属用户 ID。
     */
    @Schema(description = "记录所属用户 ID")
    private Long userId;

    /**
     * 记录所属单词本 ID。
     */
    @Schema(description = "记录所属单词本 ID")
    private Long wordbookId;

    /**
     * 生成文章时选择的词汇摘要 JSON。
     */
    @Schema(description = "生成文章时选择的词汇摘要 JSON")
    private String selectedTermsJson;

    /**
     * 用户、单词本、词汇组合、字数范围、难度和备注共同生成的缓存哈希。
     */
    @Schema(description = "用户、单词本、词汇组合、字数范围、难度和备注共同生成的缓存哈希")
    private String selectedTermHash;

    /**
     * 文章字数范围，例如 300-500。
     */
    @Schema(description = "文章字数范围，例如 300-500")
    private String wordCountRange;

    /**
     * 文章难度：easy、medium、hard。
     */
    @Schema(description = "文章难度：easy、medium、hard")
    private String difficulty;

    /**
     * 用户输入的文章生成备注或学习要求。
     */
    @Schema(description = "用户输入的文章生成备注或学习要求")
    private String remark;

    /**
     * 调用的 Agent 编码。
     */
    @Schema(description = "调用的 Agent 编码")
    private String agentCode;

    /**
     * 调用的提示词模板编码。
     */
    @Schema(description = "调用的提示词模板编码")
    private String templateCode;

    /**
     * 模型供应商。
     */
    @Schema(description = "模型供应商")
    private String provider;

    /**
     * 模型名称。
     */
    @Schema(description = "模型名称")
    private String modelName;

    /**
     * AI 会话 ID。
     */
    @Schema(description = "AI 会话 ID")
    private Long sessionId;

    /**
     * AI 原始回复。
     */
    @Schema(description = "AI 原始回复")
    private String rawContent;

    /**
     * 解析后的文章学习 JSON。
     */
    @Schema(description = "解析后的文章学习 JSON")
    private String parsedJson;

    /**
     * Token 使用量。
     */
    @Schema(description = "Token 使用量")
    private Integer tokenUsage;

    /**
     * 模型调用耗时，单位毫秒。
     */
    @Schema(description = "模型调用耗时，单位毫秒")
    private Long costTime;

    /**
     * 读取次数，缓存命中时递增。
     */
    @Schema(description = "读取次数，缓存命中时递增")
    private Integer lookupCount;

    /**
     * 最近读取时间。
     */
    @Schema(description = "最近读取时间")
    private LocalDateTime lastLookupTime;

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    public static LearningArticleStudyRecord create(Long userId, Long wordbookId, String selectedTermsJson,
                                                    String selectedTermHash, String wordCountRange, String difficulty,
                                                    String remark, String agentCode, String templateCode,
                                                    LocalDateTime now) {
        LearningArticleStudyRecord record = new LearningArticleStudyRecord();
        record.setUserId(userId);
        record.setWordbookId(wordbookId);
        record.setSelectedTermsJson(selectedTermsJson);
        record.setSelectedTermHash(selectedTermHash);
        record.setWordCountRange(wordCountRange);
        record.setDifficulty(difficulty);
        record.setRemark(remark);
        record.setAgentCode(agentCode);
        record.setTemplateCode(templateCode);
        record.setLookupCount(LearningConstants.Article.DEFAULT_LOOKUP_COUNT);
        record.setLastLookupTime(now);
        record.setDeleted(false);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    /**
     * 更新 {@code applyAiResult} 相关业务。
     */
    public void applyAiResult(AgentChatResponse response, String parsedJson, LocalDateTime now) {
        setProvider(response.getModelProvider());
        setModelName(response.getModelName());
        setSessionId(response.getSessionId());
        setRawContent(response.getContent());
        setParsedJson(parsedJson);
        setTokenUsage(response.getTokenUsage());
        setCostTime(response.getCostTime());
        setLastLookupTime(now);
        setUpdateTime(now);
    }

    /**
     * 更新 {@code touch} 相关业务。
     */
    public void touch(LocalDateTime now) {
        setLookupCount(getLookupCount() == null
                ? LearningConstants.Article.DEFAULT_LOOKUP_COUNT
                : getLookupCount() + LearningConstants.SEQUENCE_STEP);
        setLastLookupTime(now);
        setUpdateTime(now);
    }
}

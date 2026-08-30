package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import com.chandler.learning.agent.vocabulary.domain.constant.WordbookConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单词本 DO。
 * <p>
 * 管理用户自己的学习分组，默认单词本用于兜底承接新增词条。
 */
@Data
@TableName("learning_wordbook")
public class LearningWordbook extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 所属用户 ID。
     */
    @Schema(description = "所属用户 ID")
    private Long userId;

    /**
     * 单词本名称。
     */
    @Schema(description = "单词本名称")
    private String name;

    /**
     * 单词本描述。
     */
    @Schema(description = "单词本描述")
    private String description;

    /**
     * 是否默认单词本。
     */
    @Schema(description = "是否默认单词本")
    private Boolean isDefault;

    /**
     * 创建用户默认单词本，保证新用户有一个可直接加入词条的兜底分组。
     */
    public static LearningWordbook createDefault(Long userId, LocalDateTime now) {
        return create(userId, WordbookConstants.DEFAULT_NAME,
                WordbookConstants.DEFAULT_DESCRIPTION, true, now);
    }

    /**
     * 创建普通单词本。
     */
    public static LearningWordbook create(Long userId, String name, String description,
                                          boolean defaultWordbook, LocalDateTime now) {
        LearningWordbook wordbook = new LearningWordbook();
        wordbook.setUserId(userId);
        wordbook.updateProfile(name, description, defaultWordbook, now);
        wordbook.setDeleted(false);
        wordbook.setCreateTime(now);
        return wordbook;
    }

    /**
     * 更新单词本基础资料。
     */
    public void updateProfile(String name, String description, boolean defaultWordbook, LocalDateTime now) {
        setName(name);
        setDescription(description);
        setIsDefault(defaultWordbook);
        touch(now);
    }

    /**
     * 标记删除，同时取消默认单词本身份。
     */
    public void markDeleted(LocalDateTime now) {
        setDeleted(true);
        setIsDefault(false);
        touch(now);
    }

    /**
     * 切换默认单词本状态。
     */
    public void changeDefault(boolean defaultWordbook, LocalDateTime now) {
        setIsDefault(defaultWordbook);
        touch(now);
    }

    private void touch(LocalDateTime now) {
        setUpdateTime(now);
    }
}

CREATE TABLE IF NOT EXISTS vocabulary_semantic_asset (
    id BIGINT NOT NULL COMMENT '全局语义资产主键',
    language VARCHAR(16) NOT NULL DEFAULT 'en' COMMENT '词汇语言',
    normalized_term VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '标准词，跨词本唯一',
    semantic_json JSON NOT NULL COMMENT '不含词本身份的可复用语义结果',
    related_terms_json JSON NOT NULL COMMENT '相关标准词数组',
    source_job_id BIGINT NOT NULL COMMENT '最近生成该结果的任务 ID',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vocabulary_semantic_term (language, normalized_term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='跨词本全局词汇语义资产';

-- 一次性继承历史有效分析，优先保留人工结果，再按更新时间选取最新结果。
-- 相关词条 ID 转为标准词，避免把旧词本身份泄漏到新词本。
INSERT INTO vocabulary_semantic_asset
    (id, language, normalized_term, semantic_json, related_terms_json, source_job_id,
     create_by, update_by, create_time, update_time, deleted, version)
SELECT ranked.id, 'en', ranked.normalized_term,
       JSON_OBJECT('primaryGroupCode', ranked.primary_group_code,
                   'primaryGroupName', ranked.primary_group_name,
                   'domainCode', ranked.domain_code, 'subTopicCode', ranked.sub_topic_code,
                   'tagsJson', CAST(ranked.tags_json AS CHAR),
                   'difficultyLevel', ranked.difficulty_level,
                   'confidence', ranked.confidence, 'status', ranked.status),
       COALESCE((
           SELECT JSON_ARRAYAGG(LOWER(TRIM(related.normalized_term)))
           FROM JSON_TABLE(COALESCE(ranked.related_entry_ids_json, JSON_ARRAY()),
                '$[*]' COLUMNS (entry_id BIGINT PATH '$')) links
           JOIN vocabulary_catalog_entry related ON related.id = links.entry_id
           WHERE related.catalog_version_id = ranked.catalog_version_id
             AND related.deleted = 0 AND TRIM(related.normalized_term) <> ''
       ), JSON_ARRAY()),
       ranked.job_id, ranked.create_by, ranked.update_by,
       ranked.create_time, ranked.update_time, 0, 0
FROM (
    SELECT a.*, LOWER(TRIM(e.normalized_term)) COLLATE utf8mb4_bin AS normalized_term,
           ROW_NUMBER() OVER (
               PARTITION BY LOWER(TRIM(e.normalized_term)) COLLATE utf8mb4_bin
               ORDER BY (a.source = 'manual') DESC, a.update_time DESC, a.id DESC
           ) AS row_no
    FROM vocabulary_catalog_entry_analysis a
    JOIN vocabulary_catalog_entry e ON e.id = a.catalog_entry_id
    WHERE a.deleted = 0 AND e.deleted = 0
      AND a.status IN ('ready', 'low_confidence')
      AND TRIM(e.normalized_term) <> ''
      AND TRIM(a.primary_group_code) <> '' AND TRIM(a.primary_group_name) <> ''
      AND TRIM(a.domain_code) <> '' AND TRIM(a.sub_topic_code) <> ''
      AND TRIM(a.difficulty_level) <> ''
      AND JSON_TYPE(a.tags_json) = 'ARRAY'
      AND a.confidence BETWEEN 0 AND 1
) ranked
WHERE ranked.row_no = 1
ON DUPLICATE KEY UPDATE id = vocabulary_semantic_asset.id;

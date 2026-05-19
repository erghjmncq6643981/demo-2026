-- 词书词条个人学习卡快照字段。
-- 作用：学习页继续复用公共 english_vocabulary_study_record 缓存；用户加入词书时复制一份个人快照，
-- 避免其他用户重新生成公共 AI 结果后覆盖已有词书详情。
-- 请在 learning_user_wordbook_review_mysql.sql 之后执行。

SET @schema_name = DATABASE();

SET @add_snapshot_raw_content_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_raw_content MEDIUMTEXT DEFAULT NULL COMMENT ''加入词书时的 AI 原始回复快照'' AFTER note',
        'SELECT ''learning_wordbook_entry.snapshot_raw_content already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_raw_content'
);
PREPARE add_snapshot_raw_content_stmt FROM @add_snapshot_raw_content_sql;
EXECUTE add_snapshot_raw_content_stmt;
DEALLOCATE PREPARE add_snapshot_raw_content_stmt;

SET @add_snapshot_parsed_json_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_parsed_json JSON DEFAULT NULL COMMENT ''加入词书时解析出的 JSON 快照'' AFTER snapshot_raw_content',
        'SELECT ''learning_wordbook_entry.snapshot_parsed_json already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_parsed_json'
);
PREPARE add_snapshot_parsed_json_stmt FROM @add_snapshot_parsed_json_sql;
EXECUTE add_snapshot_parsed_json_stmt;
DEALLOCATE PREPARE add_snapshot_parsed_json_stmt;

SET @add_snapshot_tags_json_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_tags_json JSON DEFAULT NULL COMMENT ''加入词书时的标签快照'' AFTER snapshot_parsed_json',
        'SELECT ''learning_wordbook_entry.snapshot_tags_json already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_tags_json'
);
PREPARE add_snapshot_tags_json_stmt FROM @add_snapshot_tags_json_sql;
EXECUTE add_snapshot_tags_json_stmt;
DEALLOCATE PREPARE add_snapshot_tags_json_stmt;

SET @add_snapshot_relations_json_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_relations_json JSON DEFAULT NULL COMMENT ''加入词书时的关联词快照'' AFTER snapshot_tags_json',
        'SELECT ''learning_wordbook_entry.snapshot_relations_json already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_relations_json'
);
PREPARE add_snapshot_relations_json_stmt FROM @add_snapshot_relations_json_sql;
EXECUTE add_snapshot_relations_json_stmt;
DEALLOCATE PREPARE add_snapshot_relations_json_stmt;

SET @add_snapshot_provider_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_provider VARCHAR(50) DEFAULT NULL COMMENT ''快照使用的模型供应商'' AFTER snapshot_relations_json',
        'SELECT ''learning_wordbook_entry.snapshot_provider already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_provider'
);
PREPARE add_snapshot_provider_stmt FROM @add_snapshot_provider_sql;
EXECUTE add_snapshot_provider_stmt;
DEALLOCATE PREPARE add_snapshot_provider_stmt;

SET @add_snapshot_model_name_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_model_name VARCHAR(100) DEFAULT NULL COMMENT ''快照使用的模型名称'' AFTER snapshot_provider',
        'SELECT ''learning_wordbook_entry.snapshot_model_name already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_model_name'
);
PREPARE add_snapshot_model_name_stmt FROM @add_snapshot_model_name_sql;
EXECUTE add_snapshot_model_name_stmt;
DEALLOCATE PREPARE add_snapshot_model_name_stmt;

SET @add_snapshot_session_id_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_session_id BIGINT DEFAULT NULL COMMENT ''快照关联的 AI 会话 ID'' AFTER snapshot_model_name',
        'SELECT ''learning_wordbook_entry.snapshot_session_id already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_session_id'
);
PREPARE add_snapshot_session_id_stmt FROM @add_snapshot_session_id_sql;
EXECUTE add_snapshot_session_id_stmt;
DEALLOCATE PREPARE add_snapshot_session_id_stmt;

SET @add_snapshot_time_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE learning_wordbook_entry ADD COLUMN snapshot_time DATETIME DEFAULT NULL COMMENT ''快照生成时间'' AFTER snapshot_session_id',
        'SELECT ''learning_wordbook_entry.snapshot_time already exists'''
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'learning_wordbook_entry'
      AND COLUMN_NAME = 'snapshot_time'
);
PREPARE add_snapshot_time_stmt FROM @add_snapshot_time_sql;
EXECUTE add_snapshot_time_stmt;
DEALLOCATE PREPARE add_snapshot_time_stmt;

UPDATE learning_wordbook_entry entry
JOIN english_vocabulary_study_record vocabulary ON vocabulary.id = entry.vocabulary_id
SET entry.snapshot_raw_content = COALESCE(entry.snapshot_raw_content, vocabulary.raw_content),
    entry.snapshot_parsed_json = COALESCE(entry.snapshot_parsed_json, vocabulary.parsed_json),
    entry.snapshot_provider = COALESCE(entry.snapshot_provider, vocabulary.provider),
    entry.snapshot_model_name = COALESCE(entry.snapshot_model_name, vocabulary.model_name),
    entry.snapshot_session_id = COALESCE(entry.snapshot_session_id, vocabulary.session_id),
    entry.snapshot_time = COALESCE(entry.snapshot_time, entry.create_time, NOW())
WHERE entry.snapshot_parsed_json IS NULL
   OR entry.snapshot_raw_content IS NULL;

UPDATE learning_wordbook_entry entry
JOIN (
    SELECT vocabulary_id,
           JSON_ARRAYAGG(JSON_OBJECT(
               'id', id,
               'tagType', tag_type,
               'tagValue', tag_value,
               'displayName', display_name,
               'weight', weight
           )) AS tags_json
    FROM learning_vocabulary_tag
    GROUP BY vocabulary_id
) tag_snapshot ON tag_snapshot.vocabulary_id = entry.vocabulary_id
SET entry.snapshot_tags_json = tag_snapshot.tags_json
WHERE entry.snapshot_tags_json IS NULL;

UPDATE learning_wordbook_entry entry
JOIN (
    SELECT normalized_term,
           JSON_ARRAYAGG(JSON_OBJECT(
               'id', id,
               'relatedVocabularyId', related_vocabulary_id,
               'relatedTerm', related_term,
               'relationType', relation_type,
               'relationValue', relation_value,
               'score', score
           )) AS relations_json
    FROM (
        SELECT *
        FROM learning_vocabulary_relation
        ORDER BY normalized_term, score DESC, id ASC
    ) relation_rows
    GROUP BY normalized_term
) relation_snapshot ON relation_snapshot.normalized_term = entry.normalized_term
SET entry.snapshot_relations_json = relation_snapshot.relations_json
WHERE entry.snapshot_relations_json IS NULL;

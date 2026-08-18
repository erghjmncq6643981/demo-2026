-- 公共词本语义索引分析：支持已有公共词本按版本触发批量分析。
-- 适用于已执行 103_ai_async_task_mysql.sql 的数据库；可重复执行。

CREATE TABLE IF NOT EXISTS vocabulary_catalog_analysis_job (
    id BIGINT NOT NULL COMMENT '主键', create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID', user_id BIGINT NOT NULL COMMENT '发起分析的用户 ID',
    catalog_id BIGINT NOT NULL COMMENT '公共词本 ID', catalog_version_id BIGINT NOT NULL COMMENT '待分析词本版本 ID',
    async_task_id BIGINT DEFAULT NULL COMMENT '统一 AI 异步任务 ID', analysis_version INT NOT NULL COMMENT '词本版本内分析修订号',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、running、completed、partial_failed、failed',
    batch_size INT NOT NULL DEFAULT 100 COMMENT '每次模型调用词条数', total_count INT NOT NULL DEFAULT 0 COMMENT '待分析词条总数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '已完成词条数', failed_count INT NOT NULL DEFAULT 0 COMMENT '失败词条数',
    group_count INT NOT NULL DEFAULT 0 COMMENT '已生成语义分组数', error_message VARCHAR(1000) DEFAULT NULL COMMENT '任务级错误信息',
    started_time DATETIME DEFAULT NULL COMMENT '开始时间', finished_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除', version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id), UNIQUE KEY uk_vocabulary_catalog_analysis_version (catalog_version_id, analysis_version),
    KEY idx_vocabulary_catalog_analysis_status (catalog_version_id, status, deleted), KEY idx_vocabulary_catalog_analysis_user (user_id, update_time, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共词本语义索引分析任务';

CREATE TABLE IF NOT EXISTS vocabulary_catalog_analysis_batch (
    id BIGINT NOT NULL COMMENT '主键', create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID', update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    job_id BIGINT NOT NULL COMMENT '关联分析任务 ID', batch_no INT NOT NULL COMMENT '任务内批次序号', entry_count INT NOT NULL DEFAULT 0 COMMENT '批次词条数',
    entry_ids_json JSON NOT NULL COMMENT '批次词条 ID 列表', status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending、running、completed、failed',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已尝试次数', error_message VARCHAR(1000) DEFAULT NULL COMMENT '批次错误信息',
    started_time DATETIME DEFAULT NULL COMMENT '开始时间', finished_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除', version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id), UNIQUE KEY uk_vocabulary_catalog_analysis_batch (job_id, batch_no), KEY idx_vocabulary_catalog_analysis_batch_status (job_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共词本语义分析批次';

CREATE TABLE IF NOT EXISTS vocabulary_catalog_entry_analysis (
    id BIGINT NOT NULL COMMENT '主键', create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID', update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    job_id BIGINT NOT NULL COMMENT '关联分析任务 ID', catalog_id BIGINT NOT NULL COMMENT '公共词本 ID', catalog_version_id BIGINT NOT NULL COMMENT '公共词本版本 ID',
    catalog_entry_id BIGINT NOT NULL COMMENT '词本词条 ID', primary_group_code VARCHAR(100) DEFAULT NULL COMMENT '主语义分组编码', primary_group_name VARCHAR(160) DEFAULT NULL COMMENT '主语义分组名称',
    domain_code VARCHAR(80) DEFAULT NULL COMMENT '领域编码', sub_topic_code VARCHAR(100) DEFAULT NULL COMMENT '子主题编码', tags_json JSON DEFAULT NULL COMMENT '语义标签 JSON 数组',
    related_entry_ids_json JSON DEFAULT NULL COMMENT '同批主题相关词条 ID JSON 数组', difficulty_level VARCHAR(30) DEFAULT NULL COMMENT '难度建议', confidence DECIMAL(5,4) DEFAULT NULL COMMENT '分析置信度',
    status VARCHAR(20) NOT NULL DEFAULT 'ready' COMMENT '状态：ready、low_confidence、failed', source VARCHAR(20) NOT NULL DEFAULT 'ai' COMMENT '结果来源：ai、manual、rule',
    analysis_version INT NOT NULL COMMENT '分析修订号', raw_result_json JSON DEFAULT NULL COMMENT '受控长度的单词分析结果',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除', version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id), UNIQUE KEY uk_vocabulary_catalog_entry_analysis (job_id, catalog_entry_id),
    KEY idx_vocabulary_catalog_entry_analysis_version (catalog_version_id, status, deleted), KEY idx_vocabulary_catalog_entry_analysis_group (catalog_version_id, primary_group_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共词本词条语义索引结果';

INSERT INTO ai_prompt_template (
    id, name, code, type, tags, content, variables, description, example_input, example_output, public_template, enabled, sequence
) VALUES (
    1203, '公共词本关联分析 JSON', 'english_vocab_catalog_analysis_json', 'user', '英语,词本,关联分析,JSON',
    '请对词汇数组 {{words}} 做初步语义分析。这里只建立可复用的词本语义索引，不生成完整词卡，也不生成文章。只输出合法 JSON，不要输出 Markdown。根字段为 entries 数组，每个输入 entry_id 必须且只能对应一项。每项包含 entry_id、primary_group_code、primary_group_name、domain、sub_topic、tags、related_entry_ids、difficulty_level、confidence。primary_group_code 应稳定、简短、可用于后续分组；related_entry_ids 只能引用本批输入的 entry_id；tags 最多 6 个；confidence 为 0 到 1 的数字；语义关系只保留同义、反义、词族和主题相关，不要把搭配词放到 related_entry_ids。',
    JSON_ARRAY(JSON_OBJECT('name','words','label','词条 JSON 数组','required',true), JSON_OBJECT('name','analysis_version','label','分析修订号','required',true)),
    '为公共词本批量建立场景化学习所需的语义索引', '{"words":[{"entry_id":1,"term":"airport","meaning":"机场"}]}', '{"entries":[]}', 1, 6
)
ON DUPLICATE KEY UPDATE content = VALUES(content), variables = VALUES(variables), description = VALUES(description),
    example_input = VALUES(example_input), example_output = VALUES(example_output), enabled = VALUES(enabled), sequence = VALUES(sequence), update_time = CURRENT_TIMESTAMP;

UPDATE ai_prompt_template
SET content = '学习目的：{{learning_purpose}}。这是第 {{unit_no}} 个场景单元。新词候选 JSON：{{candidate_words}}。待挑战复习词 JSON：{{review_words}}。已完成场景标题：{{completed_scenes}}。本批新词候选必须作为 core；待挑战复习词只能作为 review 或语境辅助，不能冒充新的 core。单篇材料 core 最多 50 个，不要把历史场景已经学习过的新词重新安排到本场景。可添加场景常见具体名词作为 supplementary，但 supplementary 不得冒充词表词。输出 JSON 对象，包含 title、scenario_type、summary、learning_text、translation、vocabulary 字段。要求：1. learning_text 围绕一个主题由 2-4 个自然段落组成连贯短文，总词数 250-450 词，将核心词自然融入语境，禁止机械重复句式或无意义死循环；2. translation 为中文对照翻译；3. 短文后必须紧接着输出完整 vocabulary 数组，每项包含 term、tier、mastery_requirement、phonetic、meaning、context_meaning、accepted_spellings、meaning_question；meaning_question 包含 prompt、options（恰好 4 个中文选项）、correct_answer。只输出合法 JSON，不要输出 Markdown 代码块。',
    variables = JSON_ARRAY(JSON_OBJECT('name','learning_purpose','label','学习目的','required',true), JSON_OBJECT('name','unit_no','label','单元序号','required',true), JSON_OBJECT('name','candidate_words','label','新词候选 JSON','required',true), JSON_OBJECT('name','review_words','label','待挑战复习词 JSON','required',true), JSON_OBJECT('name','completed_scenes','label','已完成场景','required',true), JSON_OBJECT('name','target_word_count','label','目标词数','required',true)),
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocab_scene_unit_json';

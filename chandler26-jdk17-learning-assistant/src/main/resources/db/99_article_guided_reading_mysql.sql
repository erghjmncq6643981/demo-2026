-- 已执行过文章学习与场景学习脚本的数据库，升级为可记录过程和检测结果的语境精读。
SET @schema_name = DATABASE();

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
DROP PROCEDURE IF EXISTS learning_add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE learning_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_ddl TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name
    ) THEN
        SET @learning_add_column_sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_ddl);
        PREPARE learning_add_column_stmt FROM @learning_add_column_sql;
        EXECUTE learning_add_column_stmt;
        DEALLOCATE PREPARE learning_add_column_stmt;
    END IF;
END$$

CREATE PROCEDURE learning_add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_sql TEXT
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = p_table_name AND INDEX_NAME = p_index_name
    ) THEN
        SET @learning_add_index_sql = p_index_sql;
        PREPARE learning_add_index_stmt FROM @learning_add_index_sql;
        EXECUTE learning_add_index_stmt;
        DEALLOCATE PREPARE learning_add_index_stmt;
    END IF;
END$$

DELIMITER ;

CALL learning_add_column_if_missing('learning_article_study_record', 'study_status',
    '`study_status` VARCHAR(20) NOT NULL DEFAULT ''generated'' COMMENT ''精读状态：generated、in_progress、completed'' AFTER `last_lookup_time`');
CALL learning_add_column_if_missing('learning_article_study_record', 'current_stage',
    '`current_stage` VARCHAR(20) NOT NULL DEFAULT ''reading'' COMMENT ''当前阶段：reading、vocabulary、check、completed'' AFTER `study_status`');
CALL learning_add_column_if_missing('learning_article_study_record', 'practice_total',
    '`practice_total` INT NOT NULL DEFAULT 0 COMMENT ''阅读检测题目数'' AFTER `current_stage`');
CALL learning_add_column_if_missing('learning_article_study_record', 'practice_correct',
    '`practice_correct` INT NOT NULL DEFAULT 0 COMMENT ''阅读检测答对数'' AFTER `practice_total`');
CALL learning_add_column_if_missing('learning_article_study_record', 'practice_score',
    '`practice_score` INT NOT NULL DEFAULT 0 COMMENT ''阅读检测得分 0-100'' AFTER `practice_correct`');
CALL learning_add_column_if_missing('learning_article_study_record', 'started_time',
    '`started_time` DATETIME DEFAULT NULL COMMENT ''开始精读时间'' AFTER `practice_score`');
CALL learning_add_column_if_missing('learning_article_study_record', 'completed_time',
    '`completed_time` DATETIME DEFAULT NULL COMMENT ''完成精读时间'' AFTER `started_time`');
CALL learning_add_index_if_missing('learning_article_study_record', 'idx_learning_article_user_status',
    'CREATE INDEX idx_learning_article_user_status ON learning_article_study_record (user_id, study_status, deleted, update_time)');

DROP PROCEDURE IF EXISTS learning_add_column_if_missing;
DROP PROCEDURE IF EXISTS learning_add_index_if_missing;

UPDATE learning_article_study_record
SET study_status = COALESCE(NULLIF(study_status, ''), 'generated'),
    current_stage = COALESCE(NULLIF(current_stage, ''), 'reading'),
    practice_total = COALESCE(practice_total, 0),
    practice_correct = COALESCE(practice_correct, 0),
    practice_score = COALESCE(practice_score, 0);

UPDATE ai_agent
SET name = '英语语境精读助手',
    description = '把个人单词本中的目标词组织为可通读、精讲和检测的语境阅读材料',
    system_prompt = '你是严谨的英语语境精读设计师。根据学习者选择的目标词生成自然文章，并提供逐词语境讲解和可交互阅读检测。文章必须覆盖每个目标词的原始拼写，中文译文与英文逐句对应。必须只输出合法 JSON，不要输出 Markdown 代码块。',
    concise_prompt = '延续当前语境精读上下文，输出准确、连贯且可以直接检测的结构化学习内容。',
    welcome_message = '选择目标词后，我会生成一份包含通读、词汇精讲和阅读检测的语境精读材料。',
    temperature = 0.55,
    max_tokens = GREATEST(COALESCE(max_tokens, 0), 8000),
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_article';

UPDATE ai_prompt_template
SET name = '英语语境精读 JSON',
    tags = '英语,语境精读,词汇,阅读检测,JSON',
    content = '请基于以下目标词生成一份英语语境精读材料。目标词 JSON：{{words}}。文章字数范围：{{word_count_range}}。难度：{{difficulty}}，要求：{{difficulty_prompt}}。阅读主题或补充要求：{{remark}}。只输出合法 JSON。JSON 必须包含 title、level、word_count_range、difficulty、article、translation、vocabulary_focus、grammar_points、key_points、practice、study_tips。article 必须自然连贯，并至少一次使用每个目标词的原始拼写；translation 与英文句子逐句对应。vocabulary_focus 必须与输入目标词一一对应，每项包含 word、meaning、usage、sentence、translation。grammar_points 生成 2 到 4 条，每条包含 title、explanation、examples，examples 每项包含 sentence、translation。key_points 生成 3 到 6 条中文要点。practice 必须生成 3 道四选一阅读检测，每项包含 question、options（恰好 4 个英文或中文选项）、correct_answer（必须是 options 中的完整值）、explanation；答案要能从文章理解得出。study_tips 生成 2 到 4 条中文建议。',
    description = '生成可直接通读、逐词精讲和检测的语境精读材料',
    example_output = '{"title":"A Difficult Decision","article":"...","translation":"...","vocabulary_focus":[],"practice":[{"question":"What did Mia abandon?","options":["A plan","A book","A job","A trip"],"correct_answer":"A plan","explanation":"The article says she abandoned an old plan."}]}',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocab_article_json';

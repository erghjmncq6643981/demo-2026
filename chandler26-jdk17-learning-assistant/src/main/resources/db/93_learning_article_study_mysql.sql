-- 学习助手文章学习增量脚本。
-- 适用于已经完成 90/91/92 补丁的数据库；可重复执行。

CREATE TABLE IF NOT EXISTS learning_article_study_record (
    id BIGINT NOT NULL COMMENT '主键',
    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    wordbook_id BIGINT NOT NULL COMMENT '单词本 ID',
    selected_terms_json JSON NOT NULL COMMENT '生成文章时选择的词汇摘要 JSON',
    selected_term_hash VARCHAR(128) NOT NULL COMMENT '用户、单词本、词汇、字数、难度和备注生成的缓存哈希',
    word_count_range VARCHAR(20) NOT NULL COMMENT '文章字数范围：150-200、300-500、500-700、800-1000',
    difficulty VARCHAR(20) NOT NULL COMMENT '文章难度：easy、medium、hard',
    remark VARCHAR(1000) DEFAULT NULL COMMENT '用户输入的文章生成备注或学习要求',
    agent_code VARCHAR(50) NOT NULL COMMENT '调用的 Agent 编码',
    template_code VARCHAR(50) NOT NULL COMMENT '调用的提示词模板编码',
    provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    session_id BIGINT DEFAULT NULL COMMENT 'AI 会话 ID',
    raw_content MEDIUMTEXT DEFAULT NULL COMMENT 'AI 原始回复',
    parsed_json JSON DEFAULT NULL COMMENT '解析后的文章学习 JSON',
    token_usage INT DEFAULT NULL COMMENT 'Token 使用量',
    cost_time BIGINT DEFAULT NULL COMMENT '模型调用耗时，单位毫秒',
    lookup_count INT NOT NULL DEFAULT 1 COMMENT '读取次数',
    last_lookup_time DATETIME DEFAULT NULL COMMENT '最近读取时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_learning_article_user_wordbook_hash (user_id, wordbook_id, selected_term_hash, deleted),
    KEY idx_learning_article_user_time (user_id, deleted, update_time),
    KEY idx_learning_article_wordbook_time (wordbook_id, deleted, update_time),
    KEY idx_learning_article_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章学习记录';

INSERT INTO ai_agent (
    id, name, code, type, icon, description, system_prompt, concise_prompt, welcome_message,
    model_provider, model_name, temperature, max_tokens, preset_commands, enabled, sequence
) VALUES (
    1101,
    '英语文章学习助手',
    'english_article',
    'assistant',
    'file-text',
    '基于单词本中的已选词汇生成英语学习文章、语法知识点、重点总结和练习题',
    '你是一个严谨的英语文章学习助手。你需要基于用户选择的英语词汇生成自然、有学习价值的英语文章，并补充中文译文、词汇用法、语法点、重要知识点和练习题。必须只输出合法 JSON，不要输出 Markdown 代码块。',
    '你是英语文章学习助手。延续当前文章学习上下文，回答要准确、结构化、适合学习者复习。',
    '选择一组单词后，我可以把它们编进一篇可学习、可复习的英语文章。',
    NULL,
    NULL,
    0.65,
    6000,
    JSON_ARRAY(
        JSON_OBJECT('code', 'article', 'name', '生成学习文章', 'prompt', '根据所选词汇生成英语学习文章'),
        JSON_OBJECT('code', 'grammar', 'name', '讲解语法点', 'prompt', '解释文章中的重要语法点')
    ),
    1,
    2
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    system_prompt = VALUES(system_prompt),
    concise_prompt = VALUES(concise_prompt),
    welcome_message = VALUES(welcome_message),
    temperature = VALUES(temperature),
    max_tokens = VALUES(max_tokens),
    preset_commands = VALUES(preset_commands),
    enabled = VALUES(enabled),
    sequence = VALUES(sequence);

INSERT INTO ai_prompt_template (
    id, name, code, type, tags, content, variables, description, example_input, example_output,
    public_template, enabled, sequence
) VALUES (
    1101,
    '英语文章学习 JSON',
    'english_vocab_article_json',
    'user',
    '英语,文章,词汇,语法,JSON',
    '请基于以下词汇生成一份英语文章学习材料。词汇 JSON：{{words}}。文章字数范围：{{word_count_range}}。难度：{{difficulty}}，要求：{{difficulty_prompt}}。用户备注：{{remark}}。只输出合法 JSON，不要输出 Markdown。JSON 字段必须包括：title、level、word_count_range、difficulty、article、translation、vocabulary_focus、grammar_points、key_points、practice、study_tips。article 必须是一篇自然连贯的英文文章，尽量自然覆盖所有所选词汇；translation 是整篇文章的中文译文。vocabulary_focus 是数组，每项包含 word、meaning、usage、sentence、translation。grammar_points 生成 2 到 4 条，每条包含 title、explanation、examples，examples 中每项包含 sentence 和 translation。key_points 生成 3 到 6 条中文要点。practice 生成 3 道练习题，每题包含 question、answer、explanation。study_tips 生成 2 到 4 条中文学习建议。',
    JSON_ARRAY(
        JSON_OBJECT('name', 'words', 'label', '所选词汇 JSON', 'required', true),
        JSON_OBJECT('name', 'word_count_range', 'label', '文章字数范围', 'required', true),
        JSON_OBJECT('name', 'difficulty', 'label', '难度名称', 'required', true),
        JSON_OBJECT('name', 'difficulty_prompt', 'label', '难度要求', 'required', true),
        JSON_OBJECT('name', 'remark', 'label', '用户备注', 'required', true)
    ),
    '生成可解析入库的英语文章学习材料',
    '{"words":[{"term":"abandon","meaning":"放弃"}],"word_count_range":"300-500","difficulty":"适中","remark":"偏商务语境"}',
    '{"title":"A Difficult Decision","article":"...","translation":"...","vocabulary_focus":[]}',
    1,
    1,
    3
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    tags = VALUES(tags),
    content = VALUES(content),
    variables = VALUES(variables),
    description = VALUES(description),
    example_input = VALUES(example_input),
    example_output = VALUES(example_output),
    public_template = VALUES(public_template),
    enabled = VALUES(enabled),
    sequence = VALUES(sequence);

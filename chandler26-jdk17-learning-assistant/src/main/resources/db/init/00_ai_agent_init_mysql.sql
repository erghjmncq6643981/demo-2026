-- 学习助手 AI / 会话 / 模型初始化脚本。
-- 适用于全新数据库初始化。

CREATE TABLE IF NOT EXISTS ai_agent (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    code VARCHAR(50) NOT NULL COMMENT 'Agent 编码',
    type VARCHAR(20) NOT NULL DEFAULT 'chat' COMMENT '类型：chat-对话、analysis-分析、assistant-助手',
    icon VARCHAR(200) DEFAULT NULL COMMENT '图标',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    system_prompt TEXT COMMENT '完整系统提示词',
    concise_prompt TEXT COMMENT '精简系统提示词，用于后续多轮对话',
    welcome_message VARCHAR(500) DEFAULT NULL COMMENT '欢迎语',
    model_provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    temperature DECIMAL(4,2) DEFAULT NULL COMMENT '温度参数',
    max_tokens INT DEFAULT NULL COMMENT '最大输出 token',
    preset_commands JSON COMMENT '预设指令/能力 JSON',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sequence INT NOT NULL DEFAULT 0 COMMENT '排序',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_code (code),
    KEY idx_ai_agent_type (type),
    KEY idx_ai_agent_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Agent';

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    code VARCHAR(50) NOT NULL COMMENT '模板编码',
    type VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '类型：system、user、analysis',
    tags VARCHAR(200) DEFAULT NULL COMMENT '标签，逗号分隔',
    content TEXT NOT NULL COMMENT '提示词内容，支持 {{variable}} 变量',
    variables JSON COMMENT '变量定义 JSON',
    description VARCHAR(500) DEFAULT NULL COMMENT '描述',
    example_input TEXT COMMENT '示例输入',
    example_output TEXT COMMENT '示例输出',
    public_template TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公开',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    sequence INT NOT NULL DEFAULT 0 COMMENT '排序',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_prompt_template_code (code),
    KEY idx_ai_prompt_template_type (type),
    KEY idx_ai_prompt_template_tags (tags),
    KEY idx_ai_prompt_template_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 提示词模板';

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    agent_code VARCHAR(50) NOT NULL COMMENT 'Agent 编码',
    business_type VARCHAR(50) DEFAULT NULL COMMENT '业务类型',
    business_id VARCHAR(128) DEFAULT NULL COMMENT '业务 ID',
    scene_code VARCHAR(64) NOT NULL COMMENT '学习场景编码，例如 english_vocabulary',
    title VARCHAR(200) DEFAULT NULL COMMENT '会话标题',
    variables_json JSON COMMENT '会话级变量 JSON',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_ai_chat_session_user_scene (user_id, scene_code, deleted, update_time),
    KEY idx_ai_chat_session_agent (agent_code),
    KEY idx_ai_chat_session_biz (business_type, business_id),
    KEY idx_ai_chat_session_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话';

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT NOT NULL COMMENT '主键',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：system、user、assistant',
    content MEDIUMTEXT NOT NULL COMMENT '消息内容',
    token_count INT DEFAULT NULL COMMENT 'Token 数量',
    cost_time BIGINT DEFAULT NULL COMMENT '耗时，单位毫秒',
    model_provider VARCHAR(50) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    sequence INT NOT NULL DEFAULT 0 COMMENT '消息序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_ai_chat_message_session (session_id),
    KEY idx_ai_chat_message_session_sequence (session_id, sequence),
    CONSTRAINT fk_ai_chat_message_session FOREIGN KEY (session_id) REFERENCES ai_chat_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息';

CREATE TABLE IF NOT EXISTS ai_model_call_record (
    id BIGINT NOT NULL COMMENT '主键',
    session_id BIGINT DEFAULT NULL COMMENT '会话 ID',
    agent_code VARCHAR(50) DEFAULT NULL COMMENT 'Agent 编码',
    invocation_scene_code VARCHAR(64) NOT NULL DEFAULT 'general_chat' COMMENT 'AI 调用场景编码',
    provider VARCHAR(50) NOT NULL COMMENT '模型供应商',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    request_json JSON COMMENT '请求 JSON，不包含 Authorization',
    response_json MEDIUMTEXT COMMENT '原始响应 JSON',
    success TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否成功',
    error_message VARCHAR(2048) DEFAULT NULL COMMENT '错误信息',
    prompt_tokens INT DEFAULT NULL COMMENT '输入 token',
    completion_tokens INT DEFAULT NULL COMMENT '输出 token',
    total_tokens INT DEFAULT NULL COMMENT '总 token',
    latency_ms BIGINT DEFAULT NULL COMMENT '耗时，单位毫秒',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_ai_model_call_session (session_id),
    KEY idx_ai_model_call_agent (agent_code),
    KEY idx_ai_model_call_invocation_scene (invocation_scene_code, create_time),
    KEY idx_ai_model_call_provider (provider),
    KEY idx_ai_model_call_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型调用记录';

CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '配置名称',
    provider VARCHAR(50) NOT NULL COMMENT '供应商编码，例如 deepseek、kimi',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    base_url VARCHAR(255) NOT NULL COMMENT 'Base URL',
    chat_path VARCHAR(120) NOT NULL DEFAULT '/chat/completions' COMMENT 'Chat Completions 路径',
    api_key TEXT NOT NULL COMMENT 'API Key ciphertext, format enc:v1:<iv>.<ciphertext>; legacy plaintext is still readable',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认',
    sequence INT NOT NULL DEFAULT 0 COMMENT '优先级，数字越小越优先',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_ai_model_config_provider (provider, enabled, deleted),
    KEY idx_ai_model_config_priority (enabled, deleted, is_default, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置';

INSERT INTO ai_agent (
    id, name, code, type, icon, description, system_prompt, concise_prompt, welcome_message,
    model_provider, model_name, temperature, max_tokens, preset_commands, enabled, sequence
) VALUES (
    1001,
    '英语词汇学习助手',
    'english_vocabulary',
    'assistant',
    'book-open',
    '围绕英语单词和短语生成释义、例句、搭配、记忆提示和练习建议',
    '你是一个严谨的英语词汇学习助手。请围绕用户输入的英语单词或短语提供准确、简洁、结构化的学习内容。优先包含：中文释义、英文释义、词性、音标、例句、常见搭配、同义词、反义词、词根词缀或记忆提示。如果用户要求 JSON，请只输出合法 JSON，不要输出 Markdown 代码块。',
    '你是一个英语词汇学习助手。回答要准确、简洁，并延续当前词汇学习上下文。',
    '你好，我可以帮你拆解单词释义、生成例句、整理搭配，也可以为你出几道练习题。',
    NULL,
    NULL,
    0.70,
    4096,
    JSON_ARRAY(
        JSON_OBJECT('code', 'vocab_card', 'name', '词汇卡片', 'prompt', '生成词汇学习卡片'),
        JSON_OBJECT('code', 'quiz', 'name', '练习题', 'prompt', '生成词汇练习题')
    ),
    1,
    1
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
    1001,
    '英语词汇卡片 JSON',
    'english_vocab_card_json',
    'user',
    '英语,词汇,JSON',
    '请为英语词汇「{{term}}」生成学习卡片。只输出合法 JSON，不要输出 Markdown。JSON 字段包括：term、is_valid、language、phonetic.uk、phonetic.us、definitions、examples、collocations、synonyms、antonyms、word_family、memory_tips。definitions 生成 1 到 4 条，每条包含 part_of_speech、meaning、english。examples 生成 3 条对象数组，每条必须包含 sentence 和 translation，其中 sentence 是英文例句，translation 是对应中文翻译。collocations 生成 3 到 6 条对象数组，每条包含 phrase 和 meaning。synonyms、antonyms、word_family 生成对象数组，每条包含 word、part_of_speech、meaning、phonetic.uk、phonetic.us，其中 phonetic 是该相关词的英音/美音音标。中文解释要简洁准确。如果输入拼写疑似错误，请在 term 中输出你判断的最匹配标准单词，并保持 is_valid=true。',
    JSON_ARRAY(JSON_OBJECT('name', 'term', 'label', '英语单词或短语', 'required', true)),
    '生成可解析入库的英语词汇学习卡片',
    '{"term":"abandon"}',
    '{"term":"abandon","is_valid":true,"language":"en"}',
    1,
    1,
    1
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

INSERT INTO ai_prompt_template (
    id, name, code, type, tags, content, variables, description, example_input, example_output,
    public_template, enabled, sequence
) VALUES (
    1002,
    '英语词汇练习题 JSON',
    'english_vocab_quiz_json',
    'user',
    '英语,词汇,练习题,JSON',
    '请基于英语词汇「{{term}}」生成 5 道词汇练习题。只输出合法 JSON，不要输出 Markdown。JSON 字段包括：term、questions。每道题包含 type、stem、options、answer、analysis、difficulty。至少 3 道选择题，blank 类型用 ____ 表示空格，选项不能有多个正确答案。',
    JSON_ARRAY(JSON_OBJECT('name', 'term', 'label', '英语单词或短语', 'required', true)),
    '生成可解析入库的英语词汇练习题',
    '{"term":"abandon"}',
    '{"term":"abandon","questions":[]}',
    1,
    1,
    2
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

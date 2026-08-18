-- AI Agent 与提示词种子数据。
-- 依赖 schema/00_ai_schema_mysql.sql；可重复执行，不包含 API Key。

INSERT INTO ai_agent (
    id, name, code, type, icon, description, system_prompt, concise_prompt,
    welcome_message, temperature, max_tokens, preset_commands, enabled, sequence
) VALUES
(
    1001, '英语词汇学习助手', 'english_vocabulary', 'assistant', 'book-open',
    '生成释义、例句、搭配、语义关系和记忆提示',
    '你是一个严谨的英语词汇学习助手。请围绕用户输入的英语单词或短语提供准确、结构化的学习内容。需要 JSON 时只输出合法 JSON，不要输出 Markdown 代码块。',
    '你是一个英语词汇学习助手。回答准确、简洁，并延续当前词汇学习上下文。',
    '你好，我可以帮你拆解单词释义、生成例句、整理搭配和记忆提示。', 0.70, 4096,
    JSON_ARRAY(JSON_OBJECT('code','vocab_card','name','词汇卡片','prompt','生成词汇学习卡片'), JSON_OBJECT('code','quiz','name','练习题','prompt','生成词汇练习题')),
    1, 1
),
(
    1101, '英语语境精读助手', 'english_article', 'assistant', 'file-text',
    '把目标词组织为可通读、精讲和检测的语境阅读材料',
    '你是严谨的英语语境精读设计师。根据学习者选择的目标词生成自然文章，并提供逐词语境讲解和可交互阅读检测。文章必须覆盖每个目标词的原始拼写，中文译文与英文句子逐句对应。必须只输出合法 JSON，不要输出 Markdown 代码块。',
    '延续当前语境精读上下文，输出准确、连贯且可以直接检测的结构化学习内容。',
    '选择目标词后，我会生成一份包含通读、词汇精讲和阅读检测的语境精读材料。', 0.55, 8000,
    JSON_ARRAY(JSON_OBJECT('code','article','name','生成精读材料','prompt','根据所选词汇生成语境精读材料'), JSON_OBJECT('code','grammar','name','讲解语法点','prompt','解释材料中的重要语法点')),
    1, 2
),
(
    1201, '英语场景词汇规划师', 'english_vocabulary_plan', 'assistant', 'map',
    '把词表分解为可连续学习和复习的真实场景单元',
    '你是英语场景词汇规划师。必须围绕真实生活、学习、工作或旅行场景组织词汇；本批候选词全部作为本场景核心词，不能使用历史场景中的词。补充词可以来自场景常见名词。根据学习目的为每个核心词判断 recognition 或 spelling。必须只输出合法 JSON，不要输出 Markdown 代码块。',
    '延续当前词表场景计划上下文，生成下一个不重复的场景学习单元，只输出合法 JSON。',
    '我会把词表组织为可以连续学习和检查的场景单元。', 0.55, 16000,
    JSON_ARRAY(JSON_OBJECT('code','next_scene','name','生成下一个场景','prompt','从尚未首次学习的候选词中生成下一个场景学习单元')),
    1, 3
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), system_prompt = VALUES(system_prompt),
    concise_prompt = VALUES(concise_prompt), welcome_message = VALUES(welcome_message),
    temperature = VALUES(temperature), max_tokens = VALUES(max_tokens),
    preset_commands = VALUES(preset_commands), enabled = VALUES(enabled), sequence = VALUES(sequence),
    update_time = CURRENT_TIMESTAMP;

INSERT INTO ai_prompt_template (
    id, name, code, type, tags, content, variables, description, example_input, example_output,
    public_template, enabled, sequence
) VALUES
(
    1001, '英语词汇卡片 JSON', 'english_vocab_card_json', 'user', '英语,词汇,JSON',
    '请为英语词汇「{{term}}」生成学习卡片。只输出合法 JSON，不要输出 Markdown。字段包括 term、is_valid、language、phonetic.uk、phonetic.us、definitions、examples、collocations、synonyms、antonyms、word_family、memory_tips。definitions 每项包含 part_of_speech、meaning、english；examples 每项包含 sentence、translation；collocations 每项包含 phrase、meaning；synonyms、antonyms、word_family 每项包含 word、part_of_speech、meaning、phonetic.uk、phonetic.us。相关词只能包含同义词、反义词和词族，搭配只放 collocations。',
    JSON_ARRAY(JSON_OBJECT('name','term','label','英语单词或短语','required',true)),
    '生成可解析入库的英语词汇学习卡片', '{"term":"abandon"}', '{"term":"abandon","is_valid":true}', 1, 1, 1
),
(
    1002, '英语词汇练习题 JSON', 'english_vocab_quiz_json', 'user', '英语,词汇,练习题,JSON',
    '请基于英语词汇「{{term}}」生成 5 道词汇练习题。只输出合法 JSON，不要输出 Markdown。根字段为 term、questions；每道题包含 type、stem、options、answer、analysis、difficulty。至少 3 道选择题，选项不能有多个正确答案。',
    JSON_ARRAY(JSON_OBJECT('name','term','label','英语单词或短语','required',true)),
    '生成可解析入库的英语词汇练习题', '{"term":"abandon"}', '{"term":"abandon","questions":[]}', 1, 1, 2
),
(
    1101, '英语语境精读 JSON', 'english_vocab_article_json', 'user', '英语,语境精读,词汇,阅读检测,JSON',
    '请基于以下目标词生成一份英语语境精读材料。目标词 JSON：{{words}}。文章字数范围：{{word_count_range}}。难度：{{difficulty}}，要求：{{difficulty_prompt}}。阅读主题或补充要求：{{remark}}。只输出合法 JSON。JSON 必须包含 title、level、word_count_range、difficulty、article、translation、vocabulary_focus、grammar_points、key_points、practice、study_tips。article 必须自然连贯，并至少一次使用每个目标词的原始拼写；translation 与英文句子逐句对应。vocabulary_focus 必须与输入目标词一一对应，每项包含 word、meaning、usage、sentence、translation。practice 必须生成 3 道四选一阅读检测，每项包含 question、options（恰好 4 个选项）、correct_answer（必须是 options 中的完整值）、explanation。',
    JSON_ARRAY(JSON_OBJECT('name','words','label','目标词 JSON','required',true), JSON_OBJECT('name','word_count_range','label','文章字数范围','required',true), JSON_OBJECT('name','difficulty','label','难度','required',true), JSON_OBJECT('name','difficulty_prompt','label','难度要求','required',true), JSON_OBJECT('name','remark','label','补充要求','required',true)),
    '生成可通读、逐词精讲和检测的语境精读材料', '{"words":[{"term":"abandon","meaning":"放弃"}]}', '{"title":"A Difficult Decision","practice":[]}', 1, 1, 3
),
(
    1201, '英语场景词汇单元 JSON', 'english_vocab_scene_unit_json', 'user', '英语,词表,场景学习,词汇检查,JSON',
    '学习目的：{{learning_purpose}}。这是第 {{unit_no}} 个场景单元。新词候选 JSON：{{candidate_words}}。待挑战复习词 JSON：{{review_words}}。已完成场景标题：{{completed_scenes}}。本批新词候选必须作为 core；待挑战复习词只能作为 review 或语境辅助，不能冒充新的 core。单篇材料 core 最多 50 个，不要把历史场景已经学习过的新词重新安排到本场景。可添加场景常见具体名词作为 supplementary，但 supplementary 不得冒充词表词。输出 JSON 对象，包含 title、scenario_type、summary、learning_text、translation、vocabulary 字段。要求：1. learning_text 围绕一个主题由 2-4 个自然段落组成连贯短文，总词数 250-450 词，将核心词自然融入语境，禁止机械重复句式或无意义死循环；2. translation 为中文对照翻译；3. 短文后必须紧接着输出完整 vocabulary 数组，每项包含 term、tier、mastery_requirement、phonetic、meaning、context_meaning、accepted_spellings、meaning_question；meaning_question 包含 prompt、options（恰好 4 个中文选项）、correct_answer。只输出合法 JSON，不要输出 Markdown 代码块。',
    JSON_ARRAY(JSON_OBJECT('name','learning_purpose','label','学习目的','required',true), JSON_OBJECT('name','unit_no','label','单元序号','required',true), JSON_OBJECT('name','candidate_words','label','新词候选 JSON','required',true), JSON_OBJECT('name','review_words','label','待挑战复习词 JSON','required',true), JSON_OBJECT('name','completed_scenes','label','已完成场景','required',true), JSON_OBJECT('name','target_word_count','label','目标词数','required',true)),
    '根据词表按需生成一个可学习、可检查的场景单元', '{"candidate_words":[{"term":"clean"}]}', '{"title":"周末大扫除","vocabulary":[]}', 1, 1, 4
),
(
    1202, '英语词汇卡片批量 JSON', 'english_vocab_cards_batch_json', 'user', '英语,词卡,批量生成,JSON',
    '请为词汇数组 {{terms}} 批量生成学习卡片。只输出合法 JSON，不要输出 Markdown。根字段为 cards 数组，每个输入词必须且只能对应一项；字段包括 term、is_valid、language、phonetic、definitions、examples、collocations、synonyms、antonyms、word_family、memory_tips。相关词只能包含同义词、反义词和词族，搭配只放 collocations。',
    JSON_ARRAY(JSON_OBJECT('name','terms','label','词汇数组','required',true)),
    '一次模型调用生成一批独立词卡，减少请求和 Token 开销', '{"terms":["abandon","ability"]}', '{"cards":[]}', 1, 1, 5
),
(
    1203, '公共词本关联分析 JSON', 'english_vocab_catalog_analysis_json', 'user', '英语,词本,关联分析,JSON',
    '请对词汇数组 {{words}} 做初步语义分析。这里只建立可复用的词本语义索引，不生成完整词卡，也不生成文章。只输出合法 JSON，不要输出 Markdown。根字段为 entries 数组，每个输入 entry_id 必须且只能对应一项。每项包含 entry_id、primary_group_code、primary_group_name、domain、sub_topic、tags、related_entry_ids、difficulty_level、confidence。primary_group_code 应稳定、简短、可用于后续分组；related_entry_ids 只能引用本批输入的 entry_id；tags 最多 6 个；confidence 为 0 到 1 的数字；语义关系只保留同义、反义、词族和主题相关，不要把搭配词放到 related_entry_ids。',
    JSON_ARRAY(JSON_OBJECT('name','words','label','词条 JSON 数组','required',true), JSON_OBJECT('name','analysis_version','label','分析修订号','required',true)),
    '为公共词本批量建立场景化学习所需的语义索引', '{"words":[{"entry_id":1,"term":"airport","meaning":"机场"}]}', '{"entries":[]}', 1, 1, 6
)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), tags = VALUES(tags), content = VALUES(content), variables = VALUES(variables),
    description = VALUES(description), example_input = VALUES(example_input), example_output = VALUES(example_output),
    public_template = VALUES(public_template), enabled = VALUES(enabled), sequence = VALUES(sequence),
    update_time = CURRENT_TIMESTAMP;

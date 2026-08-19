-- AI 模型目录升级：仅更新内置 Agent 的推荐模型，不处理历史 Moonshot V1 模型配置和调用记录。
-- 已有数据库执行；新库直接使用 init/00_ai_agent_seed_mysql.sql。

UPDATE ai_agent
SET model_provider = 'deepseek',
    model_name = 'deepseek-v4-flash',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocabulary'
  AND deleted = 0;

UPDATE ai_agent
SET model_provider = 'deepseek',
    model_name = 'deepseek-v4-pro',
    update_time = CURRENT_TIMESTAMP
WHERE code IN ('english_article', 'english_vocabulary_plan')
  AND deleted = 0;

UPDATE ai_agent
SET
    system_prompt = REPLACE(
        system_prompt,
        '本批候选词全部作为本场景核心词，不能使用历史场景中的词。',
        '仅使用本次请求提供的候选词作为本场景核心词，候选词已由系统排除其他场景安排过的词。'
    ),
    concise_prompt = '仅根据本次请求的学习目的、候选词和复习词生成场景学习单元，只输出合法 JSON。',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocabulary_plan'
  AND deleted = 0;

UPDATE ai_prompt_template
SET content = REPLACE(
        REPLACE(content, '已完成场景标题：{{completed_scenes}}。', ''),
        '不要把历史场景已经学习过的新词重新安排到本场景。',
        '候选词已由系统排除其他场景的核心词，不需要根据历史对话自行判断。'
    ),
    variables = JSON_ARRAY(
        JSON_OBJECT('name','learning_purpose','label','学习目的','required',true),
        JSON_OBJECT('name','unit_no','label','单元序号','required',true),
        JSON_OBJECT('name','candidate_words','label','新词候选 JSON','required',true),
        JSON_OBJECT('name','review_words','label','待挑战复习词 JSON','required',true),
        JSON_OBJECT('name','target_word_count','label','目标词数','required',true)
    ),
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocab_scene_unit_json'
  AND deleted = 0;

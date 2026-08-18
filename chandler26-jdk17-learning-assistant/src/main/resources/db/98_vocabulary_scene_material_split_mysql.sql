-- 已执行过 97_update_prompt_template_mysql.sql 的数据库，补齐场景材料拆分规则。
UPDATE ai_prompt_template
SET content = REPLACE(
        REPLACE(
            content,
            '请从中选出 {{target_word_count}} 个语义相关的候选词作为 core（一般应正好选出 {{target_word_count}} 个，特殊情况下可在 8-20 之间微调）',
            '请把本批 {{target_word_count}} 个候选词全部作为 core，必须正好输出 {{target_word_count}} 个 core；单篇材料的 core 最多 50 个，若当天目标超过 50 个，系统会拆成多个场景单元分别生成。不要把之前已经进入其他场景的词重新安排到本场景'
        ),
        '；候选中适合帮助理解场景但本次不要求掌握的词可放入 extended',
        ''
    ),
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocab_scene_unit_json';

UPDATE ai_agent
SET system_prompt = REPLACE(
        system_prompt,
        '核心词只能来自候选词表。扩展词优先来自候选词表，补充词可以来自场景常见名词。',
        '本批候选词全部作为本场景核心词，不能使用历史场景中的词。补充词可以来自场景常见名词。'
    ),
    max_tokens = GREATEST(COALESCE(max_tokens, 0), 16000),
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocabulary_plan';

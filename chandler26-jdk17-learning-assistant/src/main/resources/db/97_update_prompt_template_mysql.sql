-- 97_update_prompt_template_mysql.sql
-- 更新场景单元生成 prompt 模板，增加目标词汇数控制变量

UPDATE ai_prompt_template
SET content = '学习目的：{{learning_purpose}}。这是第 {{unit_no}} 个场景单元。候选词表 JSON：{{candidate_words}}。已完成场景标题：{{completed_scenes}}。请从中选出 {{target_word_count}} 个语义相关的候选词作为 core（一般应正好选出 {{target_word_count}} 个，特殊情况下可在 8-20 之间微调）；候选中适合帮助理解场景但本次不要求掌握的词可放入 extended；可添加场景中高频的具体名词作为 supplementary，但 supplementary 不得冒充词表词。输出 JSON：title、scenario_type、summary、learning_text、translation、vocabulary。learning_text 是自然英文句子或短文，覆盖全部 core。vocabulary 每项包含 term、tier(core/extended/supplementary)、mastery_requirement(recognition/spelling)、phonetic、meaning、context_meaning、accepted_spellings、meaning_question。meaning_question 包含 prompt、options(恰好4个中文选项)、correct_answer。每个 core 都必须有 meaning_question；spelling 的 core 还必须给出 accepted_spellings。不要输出已完成场景的重复主题。',
    variables = '[\n  {"name": "learning_purpose", "label": "学习目的", "required": true},\n  {"name": "unit_no", "label": "单元序号", "required": true},\n  {"name": "candidate_words", "label": "候选词 JSON", "required": true},\n  {"name": "completed_scenes", "label": "已完成场景", "required": true},\n  {"name": "target_word_count", "label": "目标词数", "required": true}\n]'
WHERE code = 'english_vocab_scene_unit_json';

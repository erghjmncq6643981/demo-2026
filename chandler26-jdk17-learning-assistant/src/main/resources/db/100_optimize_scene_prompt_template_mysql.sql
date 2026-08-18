-- 优化场景单元提示词模板：增加篇幅与段落约束，明确禁止机械循环，确保输出完整的 vocabulary JSON。
UPDATE ai_prompt_template
SET content = '学习目的：{{learning_purpose}}。这是第 {{unit_no}} 个场景单元。候选词表 JSON：{{candidate_words}}。已完成场景标题：{{completed_scenes}}。请把本批 {{target_word_count}} 个候选词作为 core（单篇材料 core 最多 50 个）。不要把历史场景已经学习过的词重新安排到本场景。可添加场景常见具体名词作为 supplementary，但 supplementary 不得冒充词表词。输出 JSON 对象，包含 title、scenario_type、summary、learning_text、translation、vocabulary 字段。要求：1. learning_text 围绕一个主题由 2-4 个自然段落组成连贯短文，总词数 250-450 词，将核心词自然融入语境，禁止机械重复句式或无意义死循环；2. translation 为中文对照翻译；3. 短文后必须紧接着输出完整 vocabulary 数组，每项包含 term、tier、mastery_requirement、phonetic、meaning、context_meaning、accepted_spellings、meaning_question；meaning_question 包含 prompt、options（恰好 4 个中文选项）、correct_answer。只输出合法 JSON，不要输出 Markdown 代码块。',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocab_scene_unit_json';

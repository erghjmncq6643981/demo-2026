-- 词汇关联关系增强字段。
-- 如果已经执行过 learning_user_wordbook_review_mysql.sql，请执行本文件补齐搭配含义、关联词核心词性/含义和匹配信息。

ALTER TABLE learning_vocabulary_relation
    ADD COLUMN related_part_of_speech VARCHAR(50) DEFAULT NULL COMMENT '关联词核心词性' AFTER relation_value,
    ADD COLUMN related_meaning VARCHAR(512) DEFAULT NULL COMMENT '关联词或搭配核心含义' AFTER related_part_of_speech,
    ADD COLUMN match_type VARCHAR(50) DEFAULT NULL COMMENT '匹配来源：parsed_object、parsed_text、cached_exact、fuzzy' AFTER related_meaning,
    ADD COLUMN match_score INT DEFAULT NULL COMMENT '匹配分数 0-100' AFTER match_type;

CREATE INDEX idx_learning_vocabulary_relation_match
    ON learning_vocabulary_relation (normalized_term, match_type, match_score);

UPDATE ai_prompt_template
SET content = '请为英语词汇「{{term}}」生成学习卡片。只输出合法 JSON，不要输出 Markdown。JSON 字段包括：term、is_valid、language、phonetic.uk、phonetic.us、definitions、examples、collocations、synonyms、antonyms、word_family、memory_tips。definitions 生成 1 到 4 条，每条包含 part_of_speech、meaning、english。examples 生成 3 条对象数组，每条必须包含 sentence 和 translation，其中 sentence 是英文例句，translation 是对应中文翻译。collocations 生成 3 到 6 条对象数组，每条包含 phrase 和 meaning。synonyms、antonyms、word_family 生成对象数组，每条包含 word、part_of_speech、meaning、phonetic.uk、phonetic.us，其中 phonetic 是该相关词的英音/美音音标。中文解释要简洁准确。如果输入拼写疑似错误，请在 term 中输出你判断的最匹配标准单词，并保持 is_valid=true。',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocab_card_json';

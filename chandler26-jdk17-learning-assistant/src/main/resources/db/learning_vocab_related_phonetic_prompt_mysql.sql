-- 强化英语词汇学习卡片模板中的相关词音标字段要求。
-- 作用：后续 AI 生成 synonyms、antonyms、word_family 时，每条都包含相关词英音/美音音标。
-- 如果已经在前端的 Agent 管理中自定义过该模板，执行本文件会覆盖模板内容，请先按需备份。

UPDATE ai_prompt_template
SET content = '请为英语词汇「{{term}}」生成学习卡片。只输出合法 JSON，不要输出 Markdown。JSON 字段包括：term、is_valid、language、phonetic.uk、phonetic.us、definitions、examples、collocations、synonyms、antonyms、word_family、memory_tips。definitions 生成 1 到 4 条，每条包含 part_of_speech、meaning、english。examples 生成 3 条对象数组，每条必须包含 sentence 和 translation，其中 sentence 是英文例句，translation 是对应中文翻译。collocations 生成 3 到 6 条对象数组，每条包含 phrase 和 meaning。synonyms、antonyms、word_family 生成对象数组，每条包含 word、part_of_speech、meaning、phonetic.uk、phonetic.us，其中 phonetic 是该相关词的英音/美音音标。中文解释要简洁准确。如果输入拼写疑似错误，请在 term 中输出你判断的最匹配标准单词，并保持 is_valid=true。',
    update_time = CURRENT_TIMESTAMP
WHERE code = 'english_vocab_card_json';

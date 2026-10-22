-- 纠偏历史词条词卡状态：对已包含有效 AI 词卡快照（包含例句）或已关联词库的词条回刷为 ready
UPDATE learning_wordbook_entry
SET card_status = 'ready',
    card_generated_time = COALESCE(card_generated_time, snapshot_time, update_time, NOW())
WHERE card_status != 'ready'
  AND (
      vocabulary_id IS NOT NULL
      OR JSON_LENGTH(JSON_EXTRACT(snapshot_parsed_json, '$.examples')) > 0
  );

-- 对已在公共词卡库生成完整 AI 词卡但个人词条未挂载快照的词条，同步挂载并标为 ready
UPDATE learning_wordbook_entry e
JOIN english_vocabulary_study_record v ON e.normalized_term = v.normalized_term
SET e.vocabulary_id = v.id,
    e.snapshot_raw_content = COALESCE(e.snapshot_raw_content, v.raw_content),
    e.snapshot_parsed_json = CASE
        WHEN JSON_LENGTH(JSON_EXTRACT(e.snapshot_parsed_json, '$.examples')) > 0 THEN e.snapshot_parsed_json
        ELSE v.parsed_json
    END,
    e.snapshot_provider = COALESCE(e.snapshot_provider, v.provider),
    e.snapshot_model_name = COALESCE(e.snapshot_model_name, v.model_name),
    e.snapshot_session_id = COALESCE(e.snapshot_session_id, v.session_id),
    e.snapshot_time = COALESCE(e.snapshot_time, v.update_time, v.create_time),
    e.card_status = 'ready',
    e.card_generated_time = COALESCE(e.card_generated_time, v.update_time, v.create_time, NOW()),
    e.update_time = NOW()
WHERE v.deleted = 0
  AND e.card_status != 'ready'
  AND LENGTH(v.parsed_json) > 100
  AND JSON_LENGTH(JSON_EXTRACT(v.parsed_json, '$.examples')) > 0;

-- 同步修正词汇学习进度的词卡状态
UPDATE learning_word_progress p
JOIN learning_wordbook_entry e ON p.id = e.progress_id
SET p.card_status = 'ready',
    p.update_time = NOW()
WHERE e.card_status = 'ready'
  AND p.card_status != 'ready';

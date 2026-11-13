-- V124: 知识反刍场景材料补充 AI 语音文件路径与合成状态字段，支持全文 AI 真人朗读缓存。

SET @col_rumination_audio_path_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_rumination_material ADD COLUMN audio_path VARCHAR(255) DEFAULT NULL COMMENT ''AI 合成语音音频存储相对路径'' AFTER task_id',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_rumination_material'
      AND column_name = 'audio_path'
);
PREPARE stmt_rumination_audio_path FROM @col_rumination_audio_path_sql;
EXECUTE stmt_rumination_audio_path;
DEALLOCATE PREPARE stmt_rumination_audio_path;

SET @col_rumination_audio_status_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_rumination_material ADD COLUMN audio_status VARCHAR(32) NOT NULL DEFAULT ''none'' COMMENT ''语音合成状态：none 未生成，pending 排队中，running 合成中，completed 已就绪，failed 失败'' AFTER audio_path',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_rumination_material'
      AND column_name = 'audio_status'
);
PREPARE stmt_rumination_audio_status FROM @col_rumination_audio_status_sql;
EXECUTE stmt_rumination_audio_status;
DEALLOCATE PREPARE stmt_rumination_audio_status;

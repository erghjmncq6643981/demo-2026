-- P1/P2 查询路径索引。仅为已有表补充，不改变业务数据。
SET @learning_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE vocabulary_catalog ADD INDEX idx_vocabulary_catalog_public_list (status, visibility, deleted, update_time)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'vocabulary_catalog'
      AND index_name = 'idx_vocabulary_catalog_public_list'
);
PREPARE learning_index_stmt FROM @learning_index_sql;
EXECUTE learning_index_stmt;
DEALLOCATE PREPARE learning_index_stmt;

SET @learning_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_plan_unit ADD INDEX idx_learning_plan_unit_calendar (plan_id, recommended_date, deleted, unit_no)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_plan_unit'
      AND index_name = 'idx_learning_plan_unit_calendar'
);
PREPARE learning_index_stmt FROM @learning_index_sql;
EXECUTE learning_index_stmt;
DEALLOCATE PREPARE learning_index_stmt;

SET @learning_index_sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE learning_article_study_record ADD INDEX idx_learning_article_summary_page (user_id, wordbook_id, deleted, create_time)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'learning_article_study_record'
      AND index_name = 'idx_learning_article_summary_page'
);
PREPARE learning_index_stmt FROM @learning_index_sql;
EXECUTE learning_index_stmt;
DEALLOCATE PREPARE learning_index_stmt;

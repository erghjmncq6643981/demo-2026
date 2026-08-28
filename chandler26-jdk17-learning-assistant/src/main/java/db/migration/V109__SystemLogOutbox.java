package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** 为存量数据库补齐系统日志可靠异步投递 Outbox。 */
public class V109__SystemLogOutbox extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS learning_system_log_outbox (
                        id BIGINT NOT NULL COMMENT '事件主键，同时作为最终系统日志主键',
                        create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
                        update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
                        user_id BIGINT NOT NULL COMMENT '日志归属用户 ID',
                        log_type VARCHAR(64) NOT NULL COMMENT '日志类型',
                        title VARCHAR(180) NOT NULL COMMENT '业务可读日志标题',
                        detail TEXT COMMENT '有长度边界的日志详情',
                        source VARCHAR(32) NOT NULL COMMENT '来源：server、client',
                        business_type VARCHAR(64) DEFAULT NULL COMMENT '关联业务类型',
                        business_id VARCHAR(128) DEFAULT NULL COMMENT '关联业务 ID',
                        occurred_at DATETIME NOT NULL COMMENT '业务动作发生时间',
                        trace_id VARCHAR(64) DEFAULT NULL COMMENT '请求链路标识',
                        status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '投递状态：pending、processing、succeeded',
                        claim_token VARCHAR(64) DEFAULT NULL COMMENT '异步投递领取令牌',
                        processed_time DATETIME DEFAULT NULL COMMENT '成功写入最终系统日志表时间',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
                        version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                        PRIMARY KEY (id),
                        KEY idx_learning_system_log_outbox_status (status, deleted, create_time),
                        KEY idx_learning_system_log_outbox_claim (claim_token, status, deleted)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志可靠异步投递事件'
                    """);
        }
    }
}

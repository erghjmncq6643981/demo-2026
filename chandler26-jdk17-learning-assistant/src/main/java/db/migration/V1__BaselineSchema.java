package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.util.List;

/**
 * 全新数据库基线。
 * <p>
 * 存量非空数据库由 Flyway 在 107 建立基线；空数据库依次执行当前完整 schema 和幂等种子数据。
 */
public class V1__BaselineSchema extends BaseJavaMigration {

    private static final List<String> SCRIPTS = List.of(
            "db/schema/00_ai_schema_mysql.sql",
            "db/schema/10_learning_core_schema_mysql.sql",
            "db/schema/20_vocabulary_plan_schema_mysql.sql",
            "db/schema/30_article_reading_schema_mysql.sql",
            "db/schema/40_user_authorization_schema_mysql.sql",
            "db/init/00_ai_agent_seed_mysql.sql",
            "db/init/01_system_admin_seed_mysql.sql");

    @Override
    public void migrate(Context context) {
        for (String script : SCRIPTS) {
            ScriptUtils.executeSqlScript(context.getConnection(), new ClassPathResource(script));
        }
    }

    @Override
    public Integer getChecksum() {
        return 1;
    }
}

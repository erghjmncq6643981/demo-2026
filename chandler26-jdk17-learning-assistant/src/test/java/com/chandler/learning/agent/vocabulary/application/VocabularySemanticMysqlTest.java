package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.vocabulary.infrastructure.VocabularySemanticGenerationLock;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularySemanticAssetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.chandler.learning.agent.vocabulary.application.VocabularySemanticReuseServiceTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;

/** 显式启用的本机 MySQL 验证，仅创建随机前缀测试表并在结束时清理。 */
@EnabledIfEnvironmentVariable(named = "LEARNING_SEMANTIC_MYSQL_TEST", matches = "true")
class VocabularySemanticMysqlTest {
    @Test
    void verifiesHistoricalMigrationIdempotencyAndConcurrentColdWordReuse() throws Exception {
        String prefix = "codex_semantic_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String entryTable = prefix + "_entry";
        String analysisTable = prefix + "_analysis";
        String assetTable = prefix + "_asset";
        var dataSource = new DriverManagerDataSource(
                System.getenv().getOrDefault("LEARNING_SEMANTIC_TEST_URL", "jdbc:mysql://127.0.0.1:3306/demo?useSSL=false&allowPublicKeyRetrieval=true"),
                System.getenv().getOrDefault("LEARNING_DB_USERNAME", "micro"),
                System.getenv().getOrDefault("LEARNING_DB_PASSWORD", "123456"));
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch finishGeneration = new CountDownLatch(1);
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try {
                statement.execute("CREATE TABLE " + entryTable
                        + " (id BIGINT PRIMARY KEY, catalog_version_id BIGINT, normalized_term VARCHAR(255), deleted INT DEFAULT 0)");
                statement.execute("CREATE TABLE " + analysisTable + """
                         (id BIGINT PRIMARY KEY, job_id BIGINT, catalog_entry_id BIGINT, catalog_version_id BIGINT,
                          primary_group_code VARCHAR(100), primary_group_name VARCHAR(160), domain_code VARCHAR(80),
                          sub_topic_code VARCHAR(100), tags_json JSON, related_entry_ids_json JSON,
                          difficulty_level VARCHAR(30), confidence DECIMAL(5,4), status VARCHAR(20), source VARCHAR(20),
                          create_by BIGINT DEFAULT 1, update_by BIGINT DEFAULT 1,
                          create_time DATETIME DEFAULT CURRENT_TIMESTAMP, update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                          deleted INT DEFAULT 0)
                        """);
                statement.execute("INSERT INTO " + entryTable + " VALUES (1, 10, ' BANK ', 0), (2, 10, 'money', 0)");
                statement.execute("INSERT INTO " + analysisTable + """
                         (id, job_id, catalog_entry_id, catalog_version_id, primary_group_code, primary_group_name,
                          domain_code, sub_topic_code, tags_json, related_entry_ids_json, difficulty_level,
                          confidence, status, source)
                         VALUES (11,10,1,10,'finance','金融','finance','banking','["money"]','[2]','easy',0.9,'ready','ai')
                        """);
                String migration = renamed(resource("db/migration/V117__GlobalVocabularySemanticAssets.sql"),
                        entryTable, analysisTable, assetTable);
                ScriptUtils.executeSqlScript(connection, bytes(migration));
                ScriptUtils.executeSqlScript(connection, bytes(migration));
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + assetTable)) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }

                Configuration config = new Configuration(new Environment("semantic-test", new JdbcTransactionFactory(), dataSource));
                config.setMapUnderscoreToCamelCase(true);
                String xml = renamed(resource("mapper/VocabularySemanticAssetMapper.xml"),
                        entryTable, analysisTable, assetTable).replace("vocabulary_semantic:", prefix + ":");
                new XMLMapperBuilder(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        config, "semantic-test.xml", config.getSqlFragments()).parse();
                var factory = new SqlSessionFactoryBuilder().build(config);
                var mapper = spy(new SqlSessionTemplate(factory).getMapper(VocabularySemanticAssetMapper.class));
                var service = new VocabularySemanticReuseService(mapper, new VocabularySemanticGenerationLock(factory), new ObjectMapper());
                var bank = entry(101, "bank");
                var money = entry(102, "money");
                var inherited = service.resolve(job(20), List.of(bank), Map.of(101L, bank, 102L, money), false,
                        cold -> { throw new AssertionError("历史迁移结果应直接复用"); });
                assertThat(inherited.get(0).getRelatedEntryIdsJson()).isEqualTo("[102]");
                assertThat(inherited.get(0).getSource()).isEqualTo("inherited");

                CountDownLatch generationEntered = new CountDownLatch(1);
                CountDownLatch secondRead = new CountDownLatch(1);
                AtomicInteger reads = new AtomicInteger();
                AtomicInteger calls = new AtomicInteger();
                doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();
                    if (reads.incrementAndGet() >= 3) secondRead.countDown();
                    return result;
                }).when(mapper).findByTerms(anyList());
                var firstWord = entry(201, "journey");
                var secondWord = entry(301, "journey");
                var first = pool.submit(() -> service.resolve(job(30), List.of(firstWord), Map.of(201L, firstWord), false, cold -> {
                    calls.incrementAndGet();
                    generationEntered.countDown();
                    await(finishGeneration);
                    return List.of(analysis(201, "[]"));
                }));
                assertThat(generationEntered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> service.resolve(job(40), List.of(secondWord), Map.of(301L, secondWord), false, cold -> {
                    calls.incrementAndGet();
                    return List.of(analysis(301, "[]"));
                }));
                assertThat(secondRead.await(10, TimeUnit.SECONDS)).isTrue();
                finishGeneration.countDown();
                assertThat(first.get(15, TimeUnit.SECONDS)).hasSize(1);
                assertThat(second.get(15, TimeUnit.SECONDS).get(0).getSource()).isEqualTo("inherited");
                assertThat(calls).hasValue(1);
            } finally {
                finishGeneration.countDown();
                pool.shutdown();
                if (!pool.awaitTermination(35, TimeUnit.SECONDS)) pool.shutdownNow();
                // 目标全部由本测试的随机前缀构造，只清理本次创建的三张测试表。
                statement.execute("DROP TABLE IF EXISTS " + assetTable + ", " + analysisTable + ", " + entryTable);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("测试等待超时");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static String resource(String name) throws Exception {
        return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
    }

    private static ByteArrayResource bytes(String sql) {
        return new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
    }

    private static String renamed(String source, String entries, String analyses, String assets) {
        return source.replace("vocabulary_catalog_entry_analysis", analyses)
                .replace("vocabulary_catalog_entry", entries).replace("vocabulary_semantic_asset", assets);
    }
}

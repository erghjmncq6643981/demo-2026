package com.chandler.learning.agent.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止单个生产类继续膨胀，超过阈值时必须按职责拆分。 */
class SourceSizeGovernanceTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final int MAX_PRODUCTION_LINES = 1000;

    @Test
    void productionJavaFilesStayWithinResponsibilitySizeLimit() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    long lineCount = Files.readAllLines(path).size();
                    if (lineCount > MAX_PRODUCTION_LINES) {
                        violations.add(path + " (" + lineCount + " 行)");
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("无法读取源码文件: " + path, exception);
                }
            });
        }
        assertTrue(violations.isEmpty(), () -> "生产 Java 文件超过 " + MAX_PRODUCTION_LINES
                + " 行，请按用例、策略或装配职责拆分：\n" + String.join("\n", violations));
    }
}

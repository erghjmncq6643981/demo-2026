package com.chandler.fcc.admin.starter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chandler.fcc.admin.flow.FlowStudioMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.FlowDefinitionMapper;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.context.support.GenericApplicationContext;

/**
 * 使用启动入口的实际扫描规则验证新旧业务包均能装配 Mapper，无需外部数据库。
 */
class AdminMapperRegistrationTest {

    /**
     * 注册并实例化流程工作室及原有流程 Mapper，防止业务包调整导致启动失败。
     */
    @Test
    void registersFlowStudioAndExistingMappers() {
        var factory = mock(SqlSessionFactory.class);
        var configuration = new Configuration(
            new org.apache.ibatis.mapping.Environment(
                "mapper-registration",
                new org.mybatis.spring.transaction.SpringManagedTransactionFactory(),
                mock(javax.sql.DataSource.class)
            )
        );
        when(factory.getConfiguration()).thenReturn(configuration);
        try (var context = new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("sqlSessionFactory", factory);
            var policy = FccAdminApplication.class.getAnnotation(MapperScan.class);
            var scanner = new ClassPathMapperScanner(context);
            scanner.setAnnotationClass(policy.annotationClass());
            scanner.registerFilters();
            scanner.scan(policy.basePackages());
            context.refresh();
            assertNotNull(context.getBean(FlowStudioMapper.class));
            assertNotNull(context.getBean(FlowDefinitionMapper.class));
        }
    }
}

package com.sts.certificate.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.certificate.service.CertificateImageRenderer;
import com.sts.certificate.service.CertificateIssueRuleServiceImpl;
import com.sts.certificate.service.CertificateTemplateServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.mock;

@SpringBootConfiguration
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  MybatisPlusAutoConfiguration.class
})
@MapperScan({"com.sts.certificate.mapper", "com.sts.course.mapper", "com.sts.exam.mapper",
  "com.sts.training.mapper", "com.sts.org.mapper"})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class, CertificateTemplateServiceImpl.class,
  CertificateIssueRuleServiceImpl.class})
public class CertificateMapperItConfig {
  /**
   * IT 只验证数据库事务；背景对象存储由零 DB 渲染测试覆盖。
   */
  @Bean
  CertificateImageRenderer certificateImageRenderer() {
    return mock(CertificateImageRenderer.class);
  }
}

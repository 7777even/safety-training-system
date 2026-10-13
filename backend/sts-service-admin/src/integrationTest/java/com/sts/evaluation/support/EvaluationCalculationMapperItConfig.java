package com.sts.evaluation.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  MybatisPlusAutoConfiguration.class
})
@MapperScan({"com.sts.evaluation.mapper", "com.sts.training.mapper", "com.sts.course.mapper", "com.sts.org.mapper"})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class EvaluationCalculationMapperItConfig {
}

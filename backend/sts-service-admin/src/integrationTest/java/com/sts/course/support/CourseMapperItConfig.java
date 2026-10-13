package com.sts.course.support;

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

/**
 * 课程域 Mapper 集成测试的最小切片配置，与 {@code com.sts.admin.support.MapperItConfig} 同一模式。
 * <p>
 * MyBatis-Plus 拦截器（分页 + 乐观锁）与审计字段自动填充是全局基础设施，不区分业务域，
 * 直接复用 {@link MybatisPlusConfig}/{@link MybatisMetaObjectHandler}；{@code @MapperScan} 覆盖课程域及课程快照依赖的素材 Mapper。
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  MybatisPlusAutoConfiguration.class
})
@MapperScan({"com.sts.course.mapper", "com.sts.org.mapper", "com.sts.asset.mapper", "com.sts.admin.mapper",
  "com.sts.question.mapper", "com.sts.paper.mapper"})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class CourseMapperItConfig {
}

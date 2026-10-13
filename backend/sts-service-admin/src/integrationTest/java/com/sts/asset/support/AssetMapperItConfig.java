package com.sts.asset.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.asset.mapper.AssetMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 素材域 Mapper 集成测试的最小切片配置，与 {@link com.sts.course.support.CourseMapperItConfig} 同一模式。
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  MybatisPlusAutoConfiguration.class
})
@MapperScan(basePackageClasses = {AssetMapper.class, SysFileInfoMapper.class})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class AssetMapperItConfig {
}

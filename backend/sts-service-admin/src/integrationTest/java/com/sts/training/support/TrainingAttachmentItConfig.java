package com.sts.training.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.service.TrainingAttachmentService;
import com.sts.training.service.TrainingAttachmentServiceImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.mockito.Mockito.mock;

/** 培训资料事务集成测试仅装配真实 Mapper，对象存储由测试边界观测。 */
@SpringBootConfiguration
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class, MybatisPlusAutoConfiguration.class, TransactionAutoConfiguration.class})
@EnableTransactionManagement
@MapperScan({"com.sts.training.mapper", "com.sts.org.mapper", "com.sts.admin.mapper"})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class TrainingAttachmentItConfig {

  @Bean
  StorageService storageService() {
    return mock(StorageService.class);
  }

  @Bean
  TrainingAttachmentService attachmentService(TrainingAttachmentMapper attachmentMapper,
    TrainingPlanMapper planMapper, TrainingPlanStageMapper planStageMapper,
    SysFileInfoMapper fileInfoMapper, StorageService storageService) {
    return new TrainingAttachmentServiceImpl(attachmentMapper, planMapper, planStageMapper, fileInfoMapper, storageService);
  }
}

package com.sts.training.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.service.TrainingResultSyncService;
import com.sts.training.service.TrainingRewardPenaltyService;
import com.sts.training.service.TrainingRewardPenaltyServiceImpl;
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

/** 奖惩事务集成测试仅装配真实 Mapper，结果同步由测试边界观测。 */
@SpringBootConfiguration
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class, MybatisPlusAutoConfiguration.class, TransactionAutoConfiguration.class})
@EnableTransactionManagement
@MapperScan({"com.sts.training.mapper", "com.sts.org.mapper", "com.sts.admin.mapper"})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class TrainingRewardPenaltyItConfig {

  @Bean
  TrainingResultSyncService resultSyncService() {
    return mock(TrainingResultSyncService.class);
  }

  @Bean
  StorageService storageService() {
    return mock(StorageService.class);
  }

  @Bean
  TrainingRewardPenaltyService rewardPenaltyService(TrainingRewardPenaltyMapper rewardMapper,
    TrainingResultSyncService resultSync, TrainingPlanMapper planMapper,
    TrainingParticipantMapper participantMapper, SysFileInfoMapper fileMapper, StorageService storageService) {
    return new TrainingRewardPenaltyServiceImpl(rewardMapper, resultSync, planMapper, participantMapper, fileMapper, storageService);
  }
}

package com.sts.training.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.StorageService;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingExecutionLinkMapper;
import com.sts.training.mapper.TrainingOfflineScoreMapper;
import com.sts.training.mapper.TrainingOfflineSessionMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanContentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.port.TrainingAssessmentExecutionPort;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.service.TrainingAttachmentService;
import com.sts.training.service.TrainingAttachmentServiceImpl;
import com.sts.training.service.TrainingExecutionService;
import com.sts.training.service.TrainingResultSyncService;
import com.sts.training.service.TrainingWorkflowService;
import com.sts.training.service.TrainingWorkflowServiceImpl;
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

/**
 * 学员侧培训资料只读 IT：真实 Mapper 与资料服务，考试/课程/同步等无关协作者以 mock 占位，
 * 不启动 Web/Redis/Nacos，使断言落在真实 SQL 的租户过滤与类型/状态过滤上。
 */
@SpringBootConfiguration
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class, MybatisPlusAutoConfiguration.class, TransactionAutoConfiguration.class})
@EnableTransactionManagement
@MapperScan({"com.sts.training.mapper", "com.sts.org.mapper", "com.sts.admin.mapper"})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class TrainingWorkflowItConfig {

  @Bean
  com.sts.training.service.TrainingPlanApprovalService approvalService(TrainingPlanMapper plans,
    com.sts.training.mapper.TrainingPlanApprovalMapper records,SysUserMapper users,com.sts.admin.mapper.SysMenuMapper menus) {
    return new com.sts.training.service.TrainingPlanApprovalService(plans,records,users,menus);
  }

  @Bean
  StorageService storageService() {
    return mock(StorageService.class);
  }

  @Bean
  TrainingAttachmentService attachmentService(TrainingAttachmentMapper attachmentMapper,
    TrainingPlanMapper planMapper, TrainingPlanStageMapper planStageMapper,
    SysFileInfoMapper fileInfoMapper, StorageService storageService) {
    return new TrainingAttachmentServiceImpl(
      attachmentMapper, planMapper, planStageMapper, fileInfoMapper, storageService);
  }

  @Bean
  TrainingWorkflowService trainingWorkflowService(
    TrainingPlanMapper planMapper,
    TrainingParticipantMapper participantMapper,
    TrainingPlanStageMapper stageMapper,
    TrainingPlanContentMapper contentMapper,
    TrainingExecutionLinkMapper executionLinkMapper,
    TrainingOfflineSessionMapper offlineSessionMapper,
    TrainingOfflineScoreMapper offlineScoreMapper,
    TrainingRewardPenaltyMapper rewardPenaltyMapper,
    TrainingTypeMapper trainingTypeMapper,
    SysFileInfoMapper fileInfoMapper,
    SysUserMapper userMapper,
    TrainingAttachmentMapper attachmentMapper,
    TrainingAttachmentService attachmentService,
    StorageService storageService, com.sts.training.service.TrainingPlanApprovalService approvalService) {
    return new TrainingWorkflowServiceImpl(
      planMapper, participantMapper, stageMapper,
      contentMapper, executionLinkMapper,
      offlineSessionMapper, offlineScoreMapper,
      rewardPenaltyMapper,
      mock(TrainingCourseExecutionPort.class), mock(TrainingAssessmentExecutionPort.class),
      mock(TrainingExecutionService.class), mock(TrainingResultSyncService.class),
      trainingTypeMapper, fileInfoMapper, storageService, userMapper,
      attachmentMapper, attachmentService, approvalService);
  }
}

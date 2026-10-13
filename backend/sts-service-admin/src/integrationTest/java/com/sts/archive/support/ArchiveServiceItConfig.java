package com.sts.archive.support;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sts.admin.config.MybatisMetaObjectHandler;
import com.sts.admin.config.MybatisPlusConfig;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.archive.service.ArchiveService;
import com.sts.archive.service.ArchiveServiceImpl;
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
import static org.mockito.Mockito.when;

/**
 * 档案归档条件 IT：真实 Mapper + ArchiveService，不启动 Web。
 */
@SpringBootConfiguration
@ImportAutoConfiguration({
  DataSourceAutoConfiguration.class,
  DataSourceTransactionManagerAutoConfiguration.class,
  JdbcTemplateAutoConfiguration.class,
  MybatisPlusAutoConfiguration.class,
  TransactionAutoConfiguration.class
})
@EnableTransactionManagement
@MapperScan({
  "com.sts.archive.mapper",
  "com.sts.training.mapper",
  "com.sts.certificate.mapper",
  "com.sts.org.mapper",
  "com.sts.admin.mapper"
})
@Import({MybatisPlusConfig.class, MybatisMetaObjectHandler.class})
public class ArchiveServiceItConfig {

  @Bean
  ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }

  @Bean
  ArchiveService archiveService(
    com.sts.archive.mapper.ArchiveTrainingMapper archiveTrainingMapper,
    com.sts.archive.mapper.ArchiveVersionMapper archiveVersionMapper,
    com.sts.archive.mapper.ArchiveParticipantMapper archiveParticipantMapper,
    com.sts.training.mapper.TrainingPlanMapper trainingPlanMapper,
    com.sts.training.mapper.TrainingParticipantMapper trainingParticipantMapper,
    com.sts.training.mapper.TrainingRewardPenaltyMapper rewardPenaltyMapper,
    com.sts.training.mapper.TrainingAttachmentMapper attachmentMapper,
    com.sts.training.mapper.TrainingPlanContentMapper planContentMapper,
    com.sts.training.mapper.TrainingPlanStageMapper planStageMapper,
    com.sts.training.mapper.TrainingOfflineScoreMapper offlineScoreMapper,
    com.sts.certificate.mapper.CertificateCertificateMapper certificateMapper,
    com.sts.training.mapper.TrainingTypeMapper trainingTypeMapper,
    com.sts.org.mapper.OrganizationMapper organizationMapper,
    com.sts.admin.mapper.SysUserMapper userMapper,
    com.sts.admin.mapper.SysFileInfoMapper fileInfoMapper,
    com.sts.archive.mapper.ArchiveAssetMapper archiveAssetMapper,
    ObjectMapper objectMapper,
    GeneratedFileStore generatedFileStore,
    com.sts.archive.service.ArchivePdfRenderer archivePdfRenderer) {
    return new ArchiveServiceImpl(
      archiveTrainingMapper, archiveVersionMapper, archiveParticipantMapper,
      trainingPlanMapper, trainingParticipantMapper, rewardPenaltyMapper, attachmentMapper,
      planContentMapper, planStageMapper, offlineScoreMapper, certificateMapper,
      trainingTypeMapper, organizationMapper, userMapper, fileInfoMapper, archiveAssetMapper,
      objectMapper, generatedFileStore, archivePdfRenderer,
      // 此 IT 仅验证真实档案 Mapper 与版本文件，专家查课事实由定向单测覆盖。
      mock(com.sts.evaluation.service.ExpertInspectionService.class),
      mock(com.sts.evaluation.service.EvaluationRecordService.class),
      mock(com.sts.evaluation.service.EvaluationFormService.class));
  }

  @Bean
  com.sts.archive.service.ArchivePdfRenderer archivePdfRenderer() {
    return new com.sts.archive.service.ArchivePdfRenderer();
  }

  /** 固定生成文件主键，验证归档版本可持久化并回读该主键。 */
  @Bean
  GeneratedFileStore generatedFileStore() {
    GeneratedFileStore store = mock(GeneratedFileStore.class);
    when(store.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                     org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                     org.mockito.ArgumentMatchers.anyString())).thenReturn(88L);
    return store;
  }
}

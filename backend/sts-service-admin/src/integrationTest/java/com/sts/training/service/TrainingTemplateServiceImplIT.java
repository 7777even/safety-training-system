package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.training.dto.TrainingTemplateContentRequest;
import com.sts.training.dto.TrainingTemplateStageRequest;
import com.sts.training.dto.TrainingTemplateStagesReplaceRequest;
import com.sts.training.entity.TrainingTemplate;
import com.sts.training.entity.TrainingTemplateContent;
import com.sts.training.entity.TrainingTemplateStage;
import com.sts.training.mapper.*;
import com.sts.training.support.TrainingMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {TrainingMapperItConfig.class, TrainingTemplateServiceImplIT.ServiceConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingTemplateServiceImplIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private TrainingTemplateServiceImpl service;
  @Autowired
  private TrainingTemplateMapper templateMapper;
  @Autowired
  private TrainingTemplateStageMapper stageMapper;
  @Autowired
  private TrainingTemplateContentMapper contentMapper;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("replaceStages：同名阶段整体替换时旧阶段及内容正确软删")
  void replaceStages_sameName_softDeletesExistingStageAndContent() {
    Long organizationId = demoTenantId();
    Long trainingTypeId = jdbcTemplate.queryForObject(
      "SELECT training_type_id FROM sts_training_type WHERE organization_id = ? AND deleted = 0 LIMIT 1",
      Long.class, organizationId);

    TrainingTemplate template = new TrainingTemplate();
    template.setOrganizationId(organizationId);
    template.setTemplateCode("IT-TPL-" + UUID.randomUUID());
    template.setTemplateName("阶段替换集成测试模板");
    template.setTrainingTypeId(trainingTypeId);
    template.setPublishStatus("DRAFT");
    template.setStatus(1);
    templateMapper.insert(template);

    TrainingTemplateStage existingStage = stage(template.getTrainingTemplateId(), "线上学习阶段");
    stageMapper.insert(existingStage);
    TrainingTemplateContent existingContent = content(existingStage.getTemplateStageId(), "旧内容");
    contentMapper.insert(existingContent);

    TrainingTemplateStageRequest submittedStage = new TrainingTemplateStageRequest();
    submittedStage.setStageName("线上学习阶段");
    submittedStage.setStageType("ONLINE");
    submittedStage.setSortOrder(1);
    submittedStage.setStatus(1);
    TrainingTemplateContentRequest submittedContent = new TrainingTemplateContentRequest();
    submittedContent.setContentType("COURSE");
    submittedContent.setContentName("新内容");
    submittedContent.setIsRequired(1);
    submittedContent.setSortOrder(1);
    submittedContent.setStatus(1);
    submittedStage.setContents(List.of(submittedContent));
    TrainingTemplateStagesReplaceRequest request = new TrainingTemplateStagesReplaceRequest();
    request.setVersion(0);
    request.setStages(List.of(submittedStage));

    service.replaceStages(template.getTrainingTemplateId(), request);

    List<TrainingTemplateStage> activeStages = stageMapper.selectList(
      new LambdaQueryWrapper<TrainingTemplateStage>()
        .eq(TrainingTemplateStage::getTrainingTemplateId, template.getTrainingTemplateId()));
    assertThat(activeStages).singleElement()
      .satisfies(active -> assertThat(active.getTemplateStageId()).isNotEqualTo(existingStage.getTemplateStageId()));
    List<TrainingTemplateContent> activeContents = contentMapper.selectList(
      new LambdaQueryWrapper<TrainingTemplateContent>()
        .eq(TrainingTemplateContent::getTemplateStageId, activeStages.getFirst().getTemplateStageId()));
    assertThat(activeContents).singleElement()
      .satisfies(active -> assertThat(active.getContentName()).isEqualTo("新内容"));
    Long stageDeleted = jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_training_template_stage WHERE template_stage_id = ?",
      Long.class, existingStage.getTemplateStageId());
    assertThat(stageDeleted).isEqualTo(existingStage.getTemplateStageId());
    Long contentDeleted = jdbcTemplate.queryForObject(
      "SELECT deleted FROM sts_training_template_content WHERE template_content_id = ?",
      Long.class, existingContent.getTemplateContentId());
    assertThat(contentDeleted).isEqualTo(existingContent.getTemplateContentId());
  }

  private TrainingTemplateStage stage(Long templateId, String name) {
    TrainingTemplateStage stage = new TrainingTemplateStage();
    stage.setTrainingTemplateId(templateId);
    stage.setStageName(name);
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    stage.setStatus(1);
    return stage;
  }

  private TrainingTemplateContent content(Long stageId, String name) {
    TrainingTemplateContent content = new TrainingTemplateContent();
    content.setTemplateStageId(stageId);
    content.setContentType("COURSE");
    content.setContentName(name);
    content.setIsRequired(1);
    content.setSortOrder(1);
    content.setStatus(1);
    return content;
  }

  @TestConfiguration
  static class ServiceConfig {

    @Bean
    TrainingResourceAccessHelper trainingResourceAccessHelper(TrainingTypeMapper typeMapper,
                                                              TrainingStandardMapper standardMapper,
                                                              TrainingTemplateMapper templateMapper) {
      return new TrainingResourceAccessHelper(typeMapper, standardMapper, templateMapper);
    }

    @Bean
    TrainingTemplateServiceImpl trainingTemplateService(TrainingTemplateMapper templateMapper,
                                                        TrainingTemplateStageMapper stageMapper,
                                                        TrainingTemplateContentMapper contentMapper,
                                                        TrainingResourceAccessHelper resourceAccessHelper) {
      return new TrainingTemplateServiceImpl(templateMapper, stageMapper, contentMapper, resourceAccessHelper,
                                             new TrainingContentConfigCodec(new ObjectMapper()));
    }
  }
}

package com.sts.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.*;
import com.sts.training.entity.TrainingTemplate;
import com.sts.training.entity.TrainingTemplateContent;
import com.sts.training.entity.TrainingTemplateStage;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingTemplateContentMapper;
import com.sts.training.mapper.TrainingTemplateMapper;
import com.sts.training.mapper.TrainingTemplateStageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingTemplateServiceImplTest {

  private TrainingTemplateMapper templateMapper;
  private TrainingTemplateStageMapper stageMapper;
  private TrainingTemplateContentMapper contentMapper;
  private TrainingResourceAccessHelper resourceAccessHelper;
  private TrainingTemplateServiceImpl service;

  @BeforeEach
  void setUp() {
    templateMapper = mock(TrainingTemplateMapper.class);
    stageMapper = mock(TrainingTemplateStageMapper.class);
    contentMapper = mock(TrainingTemplateContentMapper.class);
    resourceAccessHelper = mock(TrainingResourceAccessHelper.class);
    service = new TrainingTemplateServiceImpl(templateMapper, stageMapper, contentMapper, resourceAccessHelper,
                                              new TrainingContentConfigCodec(new ObjectMapper()));
  }

  @Test
  @DisplayName("create：创建草稿模板")
  void create_ok() {
    when(resourceAccessHelper.requireTypeForUse(1L)).thenReturn(new TrainingType());
    when(templateMapper.insert(any(TrainingTemplate.class))).thenAnswer(inv -> {
      TrainingTemplate t = inv.getArgument(0);
      t.setTrainingTemplateId(5L);
      return 1;
    });
    TrainingTemplateCreateRequest req = new TrainingTemplateCreateRequest();
    req.setTemplateCode("T1");
    req.setTemplateName("模板1");
    req.setTrainingTypeId(1L);
    req.setStatus(1);
    assertThat(service.create(req)).isEqualTo(5L);
  }

  @Test
  @DisplayName("replaceStages：已发布模板不可编辑阶段")
  void replaceStages_published_throws() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(5L);
    template.setPublishStatus("PUBLISHED");
    when(templateMapper.selectById(5L)).thenReturn(template);
    assertThatThrownBy(() -> service.replaceStages(5L, new TrainingTemplateStagesReplaceRequest()))
      .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("replaceStages：草稿可替换阶段与内容")
  void replaceStages_draft_ok() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(5L);
    template.setPublishStatus("DRAFT");
    when(templateMapper.selectById(5L)).thenReturn(template);
    when(templateMapper.updateById(any(TrainingTemplate.class))).thenReturn(1);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(stageMapper.insert(any(TrainingTemplateStage.class))).thenAnswer(inv -> {
      TrainingTemplateStage s = inv.getArgument(0);
      s.setTemplateStageId(50L);
      return 1;
    });
    TrainingTemplateStagesReplaceRequest req = new TrainingTemplateStagesReplaceRequest();
    req.setVersion(0);
    TrainingTemplateStageRequest stage = new TrainingTemplateStageRequest();
    stage.setStageName("阶段1");
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    stage.setStatus(1);
    TrainingTemplateContentRequest content = new TrainingTemplateContentRequest();
    content.setContentType("COURSE");
    content.setContentName("课程1");
    content.setIsRequired(1);
    content.setSortOrder(1);
    content.setStatus(1);
    stage.setContents(List.of(content));
    req.setStages(List.of(stage));
    service.replaceStages(5L, req);
    verify(stageMapper).update(isNull(), any());
    verify(contentMapper).insert(any(TrainingTemplateContent.class));
  }

  @Test
  @DisplayName("replaceStages：考试不填学时落库 0")
  void replaceStages_examWithoutHours_persistsZero() {
    stubDraftReplaceStages();
    TrainingTemplateStagesReplaceRequest req = stagesRequest(examContent(null));

    service.replaceStages(5L, req);

    verify(contentMapper).insert(argThat((TrainingTemplateContent content) ->
                                           "EXAM".equals(content.getContentType())
                                             && content.getRequiredHours().compareTo(BigDecimal.ZERO) == 0));
  }

  @Test
  @DisplayName("replaceStages：考试填正数学时返回 400/8165 且不写库")
  void replaceStages_examPositiveHours_throws8165() {
    stubDraftReplaceStages();
    TrainingTemplateStagesReplaceRequest req = stagesRequest(examContent(new BigDecimal("2")));

    assertThatThrownBy(() -> service.replaceStages(5L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8165)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST)
      .hasMessageContaining("考试");
    verify(templateMapper, never()).updateById(any(TrainingTemplate.class));
    verify(contentMapper, never()).insert(any(TrainingTemplateContent.class));
    verify(stageMapper, never()).update(any(), any());
  }

  @Test
  @DisplayName("replaceStages：课程正数学时仍可保存")
  void replaceStages_courseHours_stillSaved() {
    stubDraftReplaceStages();
    TrainingTemplateContentRequest content = examContent(new BigDecimal("8"));
    content.setContentType("COURSE");
    TrainingTemplateStagesReplaceRequest req = stagesRequest(content);

    service.replaceStages(5L, req);

    verify(contentMapper).insert(argThat((TrainingTemplateContent saved) ->
                                           "COURSE".equals(saved.getContentType())
                                             && saved.getRequiredHours().compareTo(new BigDecimal("8")) == 0));
  }

  private void stubDraftReplaceStages() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(5L);
    template.setPublishStatus("DRAFT");
    when(templateMapper.selectById(5L)).thenReturn(template);
    when(templateMapper.updateById(any(TrainingTemplate.class))).thenReturn(1);
    when(stageMapper.selectList(any())).thenReturn(List.of());
    when(stageMapper.insert(any(TrainingTemplateStage.class))).thenAnswer(inv -> {
      TrainingTemplateStage s = inv.getArgument(0);
      s.setTemplateStageId(50L);
      return 1;
    });
  }

  private TrainingTemplateStagesReplaceRequest stagesRequest(TrainingTemplateContentRequest content) {
    TrainingTemplateStagesReplaceRequest req = new TrainingTemplateStagesReplaceRequest();
    req.setVersion(0);
    TrainingTemplateStageRequest stage = new TrainingTemplateStageRequest();
    stage.setStageName("阶段1");
    stage.setStageType("ONLINE");
    stage.setSortOrder(1);
    stage.setStatus(1);
    stage.setContents(List.of(content));
    req.setStages(List.of(stage));
    return req;
  }

  private TrainingTemplateContentRequest examContent(BigDecimal requiredHours) {
    TrainingTemplateContentRequest content = new TrainingTemplateContentRequest();
    content.setContentType("EXAM");
    content.setContentName("理论考");
    content.setIsRequired(1);
    content.setRequiredHours(requiredHours);
    content.setSortOrder(1);
    content.setStatus(1);
    return content;
  }

  @Test
  @DisplayName("update：版本冲突返回 409/8034")
  void update_versionConflict_throws() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(5L);
    template.setPublishStatus("DRAFT");
    template.setVersion(2);
    when(templateMapper.selectById(5L)).thenReturn(template);
    when(templateMapper.updateById(any(TrainingTemplate.class))).thenReturn(0);
    TrainingTemplateUpdateRequest req = new TrainingTemplateUpdateRequest();
    req.setVersion(1);
    req.setTemplateName("模板1");
    req.setTrainingTypeId(1L);
    req.setStatus(1);

    assertThatThrownBy(() -> service.update(5L, req))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8034);
  }

  @Test
  @DisplayName("publish：仅有课程引用且无执行配置可发布")
  void publish_courseWithoutExecutionConfig_ok() {
    TrainingTemplate template = draftTemplate();
    when(templateMapper.selectById(5L)).thenReturn(template);
    when(templateMapper.updateById(any(TrainingTemplate.class))).thenReturn(1);
    TrainingTemplateStage stage = new TrainingTemplateStage();
    stage.setTemplateStageId(50L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    TrainingTemplateContent content = new TrainingTemplateContent();
    content.setContentType("COURSE");
    content.setResourceId(99L);
    when(contentMapper.selectList(any())).thenReturn(List.of(content));

    service.publish(5L, 0);

    assertThat(template.getPublishStatus()).isEqualTo("PUBLISHED");
    verify(templateMapper).updateById(template);
  }

  @Test
  @DisplayName("publish：无阶段返回 400/8042 且保持草稿")
  void publish_emptyStages_throws8042() {
    TrainingTemplate template = draftTemplate();
    when(templateMapper.selectById(5L)).thenReturn(template);
    when(stageMapper.selectList(any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.publish(5L, 0))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8042)
      .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    assertThat(template.getPublishStatus()).isEqualTo("DRAFT");
    verify(templateMapper, never()).updateById(any(TrainingTemplate.class));
  }

  @Test
  @DisplayName("publish：有阶段无内容返回 400/8042 且保持草稿")
  void publish_stageWithoutContent_throws8042() {
    TrainingTemplate template = draftTemplate();
    when(templateMapper.selectById(5L)).thenReturn(template);
    TrainingTemplateStage stage = new TrainingTemplateStage();
    stage.setTemplateStageId(50L);
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
    when(contentMapper.selectList(any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.publish(5L, 0))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8042);
    assertThat(template.getPublishStatus()).isEqualTo("DRAFT");
    verify(templateMapper, never()).updateById(any(TrainingTemplate.class));
  }

  private static TrainingTemplate draftTemplate() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(5L);
    template.setPublishStatus("DRAFT");
    template.setVersion(0);
    return template;
  }

  @Test
  @DisplayName("replaceStages：版本冲突时不删除既有阶段")
  void replaceStages_versionConflict_doesNotDeleteStages() {
    TrainingTemplate template = new TrainingTemplate();
    template.setTrainingTemplateId(5L);
    template.setPublishStatus("DRAFT");
    template.setVersion(2);
    when(templateMapper.selectById(5L)).thenReturn(template);
    when(templateMapper.updateById(any(TrainingTemplate.class))).thenReturn(0);
    TrainingTemplateStagesReplaceRequest req = new TrainingTemplateStagesReplaceRequest();
    req.setVersion(1);
    assertThatThrownBy(() -> service.replaceStages(5L, req)).isInstanceOf(BusinessException.class);
    verify(stageMapper, never()).update(any(), any());
    verify(contentMapper, never()).update(any(), any());
  }
}

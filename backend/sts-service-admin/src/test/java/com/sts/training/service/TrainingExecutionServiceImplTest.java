package com.sts.training.service;

import com.sts.common.exception.BusinessException;
import com.sts.training.entity.TrainingExecutionLink;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.mapper.*;
import com.sts.training.port.TrainingCourseExecutionPort;
import com.sts.training.vo.TrainingExecutionLinkVO;
import com.sts.training.vo.TrainingGenerateExecutionFailureVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingExecutionServiceImplTest {

  private TrainingPlanMapper planMapper;
  private TrainingPlanStageMapper stageMapper;
  private TrainingPlanContentMapper contentMapper;
  private TrainingExecutionLinkMapper executionLinkMapper;
  private TrainingOfflineSessionMapper offlineSessionMapper;
  private TrainingExecutionProvisioner provisioner;
  private TrainingCourseExecutionPort coursePort;
  private TrainingExecutionServiceImpl service;

  @BeforeEach
  void setUp() {
    planMapper = mock(TrainingPlanMapper.class);
    stageMapper = mock(TrainingPlanStageMapper.class);
    contentMapper = mock(TrainingPlanContentMapper.class);
    executionLinkMapper = mock(TrainingExecutionLinkMapper.class);
    offlineSessionMapper = mock(TrainingOfflineSessionMapper.class);
    provisioner = mock(TrainingExecutionProvisioner.class);
    coursePort = mock(TrainingCourseExecutionPort.class);
    service = new TrainingExecutionServiceImpl(
      planMapper, stageMapper, contentMapper, executionLinkMapper, offlineSessionMapper, provisioner,
      coursePort);
  }

  @Test
  @DisplayName("generateExecutions：草稿计划拒绝")
  void generate_draftRejected() {
    when(planMapper.selectById(100L)).thenReturn(plan("DRAFT"));

    assertThatThrownBy(() -> service.generateExecutions(100L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8108);
    verifyNoInteractions(provisioner);
  }

  @Test
  @DisplayName("generateExecutions：COURSE 调用单内容 provision")
  void generate_courseUsesOfferingId() {
    publishedPlan();
    when(contentMapper.selectList(any())).thenReturn(List.of(content(300L, "COURSE")));
    when(provisioner.provision(100L, 300L)).thenReturn(link(300L, "COURSE", 888L));

    var result = service.generateExecutions(100L);

    assertThat(result.getExecutionLinksCreated()).isEqualTo(1);
    assertThat(result.getFailedCount()).isZero();
    verify(provisioner).provision(100L, 300L);
  }

  @Test
  @DisplayName("generateExecutions：EXAM 调用单内容 provision")
  void generate_examUsesAssessmentExecutionId() {
    publishedPlan();
    TrainingPlanContent content = content(301L, "EXAM");
    content.setAssessmentMode("QUIZ");
    when(contentMapper.selectList(any())).thenReturn(List.of(content));
    when(provisioner.provision(100L, 301L)).thenReturn(link(301L, "EXAM", 777L));

    var result = service.generateExecutions(100L);

    assertThat(result.getExecutionLinksCreated()).isEqualTo(1);
    verify(provisioner).provision(100L, 301L);
  }

  @Test
  @DisplayName("generateExecutions：单内容失败不阻止其他内容")
  void generate_oneFailureKeepsOthers() {
    publishedPlan();
    when(contentMapper.selectList(any())).thenReturn(List.of(content(300L, "COURSE"), content(301L, "EXAM")));
    when(provisioner.provision(100L, 300L)).thenReturn(link(300L, "COURSE", 888L));
    when(provisioner.provision(100L, 301L))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 8142, "来源考试不存在"));

    var result = service.generateExecutions(100L);

    assertThat(result.getExecutionLinksCreated()).isEqualTo(1);
    assertThat(result.getFailedCount()).isEqualTo(1);
    assertThat(result.getFailures()).hasSize(1);
    assertThat(result.getFailures().getFirst().getPlanContentId()).isEqualTo(301L);
    assertThat(result.getFailures().getFirst().getCode()).isEqualTo(8142);
    assertThat(result.getFailures().getFirst().getMessage()).isEqualTo("来源考试不存在");
  }

  @Test
  @DisplayName("generateExecutions：已存在执行计为跳过")
  void generate_existingCountedAsSkipped() {
    publishedPlan();
    when(contentMapper.selectList(any())).thenReturn(List.of(content(300L, "COURSE")));
    TrainingExecutionLink existing = new TrainingExecutionLink();
    existing.setExecutionLinkId(9L);
    existing.setExecutionId(888L);
    when(executionLinkMapper.selectOne(any())).thenReturn(existing);
    when(provisioner.provision(100L, 300L)).thenReturn(link(300L, "COURSE", 888L));

    var result = service.generateExecutions(100L);

    assertThat(result.getSkippedExisting()).isEqualTo(1);
    assertThat(result.getExecutionLinksCreated()).isZero();
  }

  @Test
  @DisplayName("generateExecutions：非 BusinessException 只返回脱敏信息")
  void generate_unexpectedErrorIsSanitized() {
    publishedPlan();
    when(contentMapper.selectList(any())).thenReturn(List.of(content(300L, "COURSE")));
    when(provisioner.provision(100L, 300L)).thenThrow(new IllegalStateException("jdbc url password=secret"));

    var result = service.generateExecutions(100L);

    TrainingGenerateExecutionFailureVO failure = result.getFailures().getFirst();
    assertThat(failure.getCode()).isEqualTo(8143);
    assertThat(failure.getMessage()).isEqualTo("执行生成失败");
    assertThat(failure.getMessage()).doesNotContain("password");
  }

  @Test
  @DisplayName("generateExecutions：COURSE 缺阅卷人时失败项带上已落下的 offeringId")
  void generate_courseReviewerFailureIncludesOfferingId() {
    publishedPlan();
    when(contentMapper.selectList(any())).thenReturn(List.of(content(300L, "COURSE")));
    when(provisioner.provision(100L, 300L))
      .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST, 5068, "含主观题的课程测验必须配置阅卷人"));
    when(coursePort.findOfferingIdByPlanContentId(300L)).thenReturn(888L);

    var result = service.generateExecutions(100L);

    assertThat(result.getFailedCount()).isEqualTo(1);
    TrainingGenerateExecutionFailureVO failure = result.getFailures().getFirst();
    assertThat(failure.getCode()).isEqualTo(5068);
    assertThat(failure.getPlanContentId()).isEqualTo(300L);
    assertThat(failure.getOfferingId()).isEqualTo(888L);
  }

  private void publishedPlan() {
    when(planMapper.selectById(100L)).thenReturn(plan("PUBLISHED"));
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setPlanStageId(200L);
    stage.setTrainingPlanId(100L);
    stage.setStartTime(LocalDateTime.now());
    stage.setEndTime(LocalDateTime.now().plusDays(1));
    when(stageMapper.selectList(any())).thenReturn(List.of(stage));
  }

  private static TrainingPlan plan(String status) {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(100L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus(status);
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setLocation("loc");
    return plan;
  }

  private static TrainingPlanContent content(Long id, String type) {
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(id);
    content.setPlanStageId(200L);
    content.setContentType(type);
    content.setContentName(type);
    content.setResourceId(999L);
    content.setResourceVersionId(50L);
    return content;
  }

  private static TrainingExecutionLinkVO link(Long contentId, String type, Long executionId) {
    TrainingExecutionLinkVO vo = new TrainingExecutionLinkVO();
    vo.setTrainingPlanId(100L);
    vo.setPlanContentId(contentId);
    vo.setExecutionType(type);
    vo.setExecutionId(executionId);
    return vo;
  }
}

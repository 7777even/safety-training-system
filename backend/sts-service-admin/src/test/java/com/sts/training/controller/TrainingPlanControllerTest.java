package com.sts.training.controller;

import com.sts.training.service.TrainingPlanService;
import com.sts.training.vo.TrainingPublishIssueVO;
import com.sts.training.vo.TrainingPublishPrecheckVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainingPlanControllerTest {

  private TrainingPlanService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(TrainingPlanService.class);
    mvc = MockMvcBuilders.standaloneSetup(new TrainingPlanController(service)).build();
  }

  @Test
  @DisplayName("创建计划缺少培训性质返回 400 且不进入 Service")
  void createMissingTrainingNatureReturnsBadRequest() throws Exception {
    mvc.perform(post("/training/plans")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(validBodyWithoutNature()))
      .andExpect(status().isBadRequest());

    verify(service, never()).create(any());
  }

  @Test
  @DisplayName("创建计划不接受历史兼容值 UNSPECIFIED")
  void createRejectsUnspecifiedTrainingNature() throws Exception {
    mvc.perform(post("/training/plans")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(validBodyWithoutNature().replace("\"status\":1",
                                                            "\"trainingNature\":\"UNSPECIFIED\",\"status\":1")))
      .andExpect(status().isBadRequest());

    verify(service, never()).create(any());
  }

  @Test
  @DisplayName("草稿计划 apply-template 透传版本和模板 ID")
  void applyTemplateDelegatesToService() throws Exception {
    mvc.perform(post("/training/plans/100/apply-template")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1,\"sourceTemplateId\":20}"))
      .andExpect(status().isOk());

    verify(service).applyTemplate(eq(100L), argThat(request ->
                                                      Integer.valueOf(1).equals(request.getVersion())
                                                        && Long.valueOf(20L).equals(request.getSourceTemplateId())));
  }

  @Test
  @DisplayName("apply-template 缺少来源模板 ID 返回 400 且不进入 Service")
  void applyTemplateMissingSourceTemplateReturnsBadRequest() throws Exception {
    mvc.perform(post("/training/plans/100/apply-template")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1}"))
      .andExpect(status().isBadRequest());

    verify(service, never()).applyTemplate(any(), any());
  }

  @Test
  @DisplayName("发布预检返回候选不足的规则级定位字段")
  void publishPrecheckReturnsQuestionRuleDetails() throws Exception {
    TrainingPublishIssueVO issue = new TrainingPublishIssueVO();
    issue.setCode(8166);
    issue.setMessage("第 2 条规则，需要 10 题，当前可用 7 题");
    issue.setStageId(2001L);
    issue.setStageName("岗位理论练习");
    issue.setPlanContentId(3001L);
    issue.setContentType("QUESTION_BANK");
    issue.setContentName("受限空间安全练习");
    issue.setResourceId(601L);
    issue.setResourceName("受限空间题库");
    issue.setRuleIndex(2);
    issue.setQuestionType("SINGLE");
    issue.setDifficulty("EASY");
    issue.setKnowledgePointId(101L);
    issue.setRequiredCount(10);
    issue.setAvailableCount(7);
    issue.setSuggestion("请增加符合筛选条件的已发布题目，或调整抽题规则与抽取数量");
    TrainingPublishPrecheckVO response = new TrainingPublishPrecheckVO();
    response.setTrainingPlanId(1L);
    response.setReady(false);
    response.setIssues(List.of(issue));
    when(service.precheck(1L)).thenReturn(response);

    mvc.perform(post("/training/plans/1/publish-precheck"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.issues[0].code").value(8166))
      .andExpect(jsonPath("$.issues[0].stageName").value("岗位理论练习"))
      .andExpect(jsonPath("$.issues[0].ruleIndex").value(2))
      .andExpect(jsonPath("$.issues[0].requiredCount").value(10))
      .andExpect(jsonPath("$.issues[0].availableCount").value(7))
      .andExpect(jsonPath("$.issues[0].suggestion").isNotEmpty());
  }

  private String validBodyWithoutNature() {
    return "{\"planCode\":\"P1\",\"planName\":\"初培计划\",\"trainingTypeId\":1,"
      + "\"startTime\":\"2026-09-01T09:00:00\",\"endTime\":\"2026-09-02T18:00:00\",\"status\":1}";
  }
}

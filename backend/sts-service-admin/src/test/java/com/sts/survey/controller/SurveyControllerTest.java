package com.sts.survey.controller;

import com.sts.survey.dto.SurveyCopyRequest;
import com.sts.survey.service.SurveyInstanceService;
import com.sts.survey.service.SurveyService;
import com.sts.survey.vo.SurveyPreviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 问卷最小闭环 HTTP 透传，以及超范围入口不再暴露。
 */
class SurveyControllerTest {

  private SurveyService surveyService;
  private SurveyInstanceService surveyInstanceService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    surveyService = mock(SurveyService.class);
    surveyInstanceService = mock(SurveyInstanceService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new SurveyController(surveyService, surveyInstanceService)).build();
  }

  @Test
  @DisplayName("问卷独立发布路径不再暴露")
  void publish_unmapped() throws Exception {
    mockMvc.perform(post("/survey/surveys/1/publish")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
      .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("复杂目标绑定路径不再暴露")
  void bindParticipants_unmapped() throws Exception {
    mockMvc.perform(post("/survey/instances/1/participants")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"selection\":{\"selectionType\":\"ALL\",\"subjectIds\":[]}}"))
      .andExpect(status().isNotFound());
    verifyNoInteractions(surveyInstanceService);
  }

  @Test
  @DisplayName("复制问卷透传到 Service")
  void copy_delegates() throws Exception {
    when(surveyService.copy(eq(1L), any(SurveyCopyRequest.class))).thenReturn(9L);
    mockMvc.perform(post("/survey/surveys/1/copy")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"surveyCode\":\"S-COPY\",\"surveyName\":\"复制问卷\"}"))
      .andExpect(status().isOk());
    verify(surveyService).copy(eq(1L), any(SurveyCopyRequest.class));
  }

  @Test
  @DisplayName("双端预览透传到 Service")
  void preview_delegates() throws Exception {
    when(surveyService.preview(1L, "PC")).thenReturn(new SurveyPreviewVO());
    mockMvc.perform(get("/survey/surveys/1/preview").param("client", "PC"))
      .andExpect(status().isOk());
    verify(surveyService).preview(1L, "PC");
  }

  @Test
  @DisplayName("填写页与统计路径存在")
  void fillAndStats_mapped() throws Exception {
    mockMvc.perform(get("/survey/instances/1/fill")).andExpect(status().isOk());
    mockMvc.perform(get("/survey/instances/1/stats")).andExpect(status().isOk());
    mockMvc.perform(get("/survey/instances/1")).andExpect(status().isOk());
    verify(surveyInstanceService).getFill(1L);
    verify(surveyInstanceService).stats(1L);
    verify(surveyInstanceService).get(1L);
  }
}

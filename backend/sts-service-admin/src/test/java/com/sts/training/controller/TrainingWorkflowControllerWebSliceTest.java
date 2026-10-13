package com.sts.training.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.training.service.TrainingCoverFile;
import com.sts.training.service.TrainingWorkflowService;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrainingWorkflowControllerWebSliceTest {

  private TrainingWorkflowService service;
  private MockMvc mockMvc;
  private MockedStatic<StpUtil> stp;

  @BeforeEach
  void setUp() {
    service = mock(TrainingWorkflowService.class);
    stp = mockStatic(StpUtil.class);
    stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);
    mockMvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service))
      .setControllerAdvice(new HttpApiExceptionHandler(), new ResultEasyTransAdvice(
        mock(TranslationExecutor.class), new ObjectMapper()))
      .build();
  }

  @AfterEach
  void tearDown() {
    stp.close();
  }

  @Test
  void coverReturnsRawBytesWithoutResultEnvelope() throws Exception {
    when(service.openLearnerPlanCover(20L, 88L)).thenReturn(new TrainingCoverFile(
      new ByteArrayResource("png".getBytes()), "计划封面.png", "image/png", 3L, "\"900-0-3\""));

    mockMvc.perform(get("/training/learner/plans/20/cover"))
      .andExpect(status().isOk())
      .andExpect(content().contentType("image/png"))
      .andExpect(content().bytes("png".getBytes()))
      .andExpect(header().string("ETag", "\"900-0-3\""));
  }

  @Test
  void invisiblePlanCoverUses8155Envelope() throws Exception {
    when(service.openLearnerPlanCover(20L, 88L))
      .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 8155, "培训任务对当前学员不可见"));

    mockMvc.perform(get("/training/learner/plans/20/cover"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(8155));
  }

  @Test
  void missingLearnerOfflineSessionUses8155Envelope() throws Exception {
    when(service.getLearnerOfflineSession(301L, 88L))
      .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 8155, "场次对当前学员不可见"));

    mockMvc.perform(get("/training/learner/offline-sessions/301"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(8155));
  }
}

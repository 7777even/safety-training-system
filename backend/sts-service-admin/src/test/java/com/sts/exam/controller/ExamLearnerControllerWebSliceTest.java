package com.sts.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.exam.service.ExamLearnerService;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExamLearnerControllerWebSliceTest {

  private ExamLearnerService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(ExamLearnerService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new ExamLearnerController(service))
      .setControllerAdvice(new HttpApiExceptionHandler(), new ResultEasyTransAdvice(
        mock(TranslationExecutor.class), new ObjectMapper()))
      .build();
  }

  @Test
  void coverReturnsRawBytesWithoutResultEnvelope() throws Exception {
    when(service.openCover(1L)).thenReturn(new ExamLearnerService.ExamCoverFile(
      new ByteArrayResource("png".getBytes()), "考试封面.png", "image/png", 3L, "\"900-0-3\""));

    mockMvc.perform(get("/exam/learner/exams/1/cover"))
      .andExpect(status().isOk())
      .andExpect(content().contentType("image/png"))
      .andExpect(content().bytes("png".getBytes()))
      .andExpect(header().string("ETag", "\"900-0-3\""));
  }

  @Test
  void unseenExamCoverUses7001Envelope() throws Exception {
    when(service.openCover(1L)).thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 7001, "考试不存在"));

    mockMvc.perform(get("/exam/learner/exams/1/cover"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(7001));
  }

  @Test
  void missingCoverUses7071Envelope() throws Exception {
    when(service.openCover(1L)).thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 7071, "考试封面不存在"));

    mockMvc.perform(get("/exam/learner/exams/1/cover"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(7071));
  }
}

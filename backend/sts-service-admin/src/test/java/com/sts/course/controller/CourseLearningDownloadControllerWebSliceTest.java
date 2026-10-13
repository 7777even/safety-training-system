package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.LearnerCourseDownloadService;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseLearningDownloadControllerWebSliceTest {

  private LearnerCourseDownloadService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(LearnerCourseDownloadService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseLearningDownloadController(service))
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(mock(TranslationExecutor.class), new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("附件或源文件失效返回真实 404 与业务码 5089")
  void unavailableFileReturns404Envelope() throws Exception {
    when(service.open(11L, 30L, null))
      .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 5089, "附件素材或源文件当前不可下载"));

    mockMvc.perform(get("/course/learning/enrollments/11/download-assets/30/file"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(5089))
      .andExpect(jsonPath("$.message").value("附件素材或源文件当前不可下载"));
  }

  @Test
  @DisplayName("非报名本人返回真实 403 与业务码 5081")
  void nonOwnerReturns403Envelope() throws Exception {
    when(service.open(11L, 30L, null))
      .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, 5081, "无权访问该报名学习数据"));

    mockMvc.perform(get("/course/learning/enrollments/11/download-assets/30/file"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value(5081));
  }
}

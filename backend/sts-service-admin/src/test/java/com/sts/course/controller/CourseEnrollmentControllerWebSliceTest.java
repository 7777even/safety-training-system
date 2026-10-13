package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseEnrollmentService;
import com.sts.course.vo.CourseEnrollmentListItemVO;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseEnrollmentControllerWebSliceTest {

  private CourseEnrollmentService courseEnrollmentService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    courseEnrollmentService = mock(CourseEnrollmentService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseEnrollmentController(courseEnrollmentService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("pageEnrollments PageResult 被 Result 包装")
  void pageEnrollments_isWrapped() throws Exception {
    CourseEnrollmentListItemVO vo = new CourseEnrollmentListItemVO();
    vo.setCourseEnrollmentId(1000L);
    vo.setUserId(99L);
    when(courseEnrollmentService.pageEnrollments(eq(100L), any()))
      .thenReturn(new PageResult<>(List.of(vo), 1L));

    mockMvc.perform(get("/course/offerings/100/enrollments"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.list[0].userId").value(99));
  }

  @Test
  @DisplayName("selfEnroll 成功返回被 Result 包装的报名 ID")
  void selfEnroll_success_isWrapped() throws Exception {
    when(courseEnrollmentService.selfEnroll(100L)).thenReturn(1000L);

    mockMvc.perform(post("/course/offerings/100/self-enroll"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data").value(1000));
  }

  @Test
  @DisplayName("assignEnrollments 业务异常 → 400 / 5072")
  void assignEnrollments_businessException_yields400() throws Exception {
    doThrow(new BusinessException(HttpStatus.BAD_REQUEST, 5072, "开课未发布"))
      .when(courseEnrollmentService).assign(eq(100L), any());

    mockMvc.perform(post("/course/offerings/100/enrollments")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"selection\":{\"selectionType\":\"USER\",\"subjectIds\":[99]}}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(5072));
  }
}

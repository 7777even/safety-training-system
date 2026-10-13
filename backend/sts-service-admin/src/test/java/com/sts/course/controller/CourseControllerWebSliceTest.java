package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseService;
import com.sts.course.vo.CourseListItemVO;
import com.sts.course.vo.CourseVO;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CourseController} Web 切片测试，同 {@code CourseCategoryControllerWebSliceTest} 模式。
 */
class CourseControllerWebSliceTest {

  private CourseService courseService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    courseService = mock(CourseService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseController(courseService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("PageResult 返回被包成 Result：code=0 / data.total/list")
  void pageCourses_isWrapped() throws Exception {
    CourseListItemVO vo = new CourseListItemVO();
    vo.setCourseId(1L);
    vo.setCourseName("安全教育入门");
    when(courseService.pageCourses(any())).thenReturn(new PageResult<>(List.of(vo), 1L));

    mockMvc.perform(get("/course/courses"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.list[0].courseName").value("安全教育入门"));
  }

  @Test
  @DisplayName("getCourse 裸对象返回被包成 Result")
  void getCourse_isWrapped() throws Exception {
    CourseVO vo = new CourseVO();
    vo.setCourseId(1L);
    vo.setCourseCode("SAFETY-001");
    when(courseService.getCourse(1L)).thenReturn(vo);

    mockMvc.perform(get("/course/courses/1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.courseCode").value("SAFETY-001"));
  }

  @Test
  @DisplayName("Service 抛 BusinessException(409) → 响应真为 409")
  void businessException_yieldsRealHttpStatus() throws Exception {
    when(courseService.createCourse(any()))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 5012, "课程编码已存在"));

    mockMvc.perform(post("/course/courses")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"courseCode\":\"SAFETY-001\",\"courseName\":\"安全教育入门\","
                                 + "\"contentMode\":\"MULTI_CHAPTER\",\"credit\":1,\"pointMode\":\"NONE\","
                                 + "\"pointValue\":0,\"sourceDownloadMode\":\"NONE\",\"isSpeedPlayAllowed\":0,"
                                 + "\"isStudyAnticheatEnabled\":0,\"isStudyDescriptionEnabled\":1,\"isWatermarkEnabled\":0}"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(5012));
  }

  @Test
  @DisplayName("@Valid 失败（内容模式非法值）→ 400 / code=1001，不进 service")
  void validationFailure_returns400() throws Exception {
    mockMvc.perform(post("/course/courses")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"courseCode\":\"SAFETY-001\",\"courseName\":\"安全教育入门\","
                                 + "\"contentMode\":\"WEIRD\",\"credit\":1,\"pointMode\":\"NONE\","
                                 + "\"pointValue\":0,\"sourceDownloadMode\":\"NONE\",\"isSpeedPlayAllowed\":0,"
                                 + "\"isStudyAnticheatEnabled\":0,\"isStudyDescriptionEnabled\":1,\"isWatermarkEnabled\":0}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(1001));

    verify(courseService, never()).createCourse(any());
  }
}

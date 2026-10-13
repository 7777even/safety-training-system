package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.dto.CoursePublishRequest;
import com.sts.course.service.CoursePublishService;
import com.sts.course.vo.CourseVersionListItemVO;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoursePublishControllerWebSliceTest {

  private CoursePublishService coursePublishService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    coursePublishService = mock(CoursePublishService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CoursePublishController(coursePublishService))
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("publish 成功返回被 Result 包装的新版本 ID")
  void publish_success_isWrapped() throws Exception {
    when(coursePublishService.publish(eq(10L), any(CoursePublishRequest.class))).thenReturn(200L);

    mockMvc.perform(post("/course/courses/10/publish")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"version\":1,\"versionNote\":\"首次发布\"}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data").value(200));
  }

  @Test
  @DisplayName("listVersions 列表被 Result 包装")
  void listVersions_isWrapped() throws Exception {
    CourseVersionListItemVO vo = new CourseVersionListItemVO();
    vo.setCourseVersionId(1L);
    vo.setVersionNo(1);
    when(coursePublishService.listVersions(10L)).thenReturn(List.of(vo));

    mockMvc.perform(get("/course/courses/10/versions"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data[0].versionNo").value(1));
  }

  @Test
  @DisplayName("publish 业务异常 → 真实 HTTP 状态与错误码")
  void publish_businessException_yieldsRealHttpStatus() throws Exception {
    when(coursePublishService.publish(eq(10L), any(CoursePublishRequest.class)))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 5061, "版本号不匹配"));

    mockMvc.perform(post("/course/courses/10/publish")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"version\":2}"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(5061));
  }
}

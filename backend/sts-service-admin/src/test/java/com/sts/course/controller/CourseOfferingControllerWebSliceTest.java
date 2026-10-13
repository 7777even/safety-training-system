package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseOfferingService;
import com.sts.course.vo.CourseOfferingListItemVO;
import com.sts.course.vo.CourseOfferingVO;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseOfferingControllerWebSliceTest {

  private CourseOfferingService courseOfferingService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    courseOfferingService = mock(CourseOfferingService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseOfferingController(courseOfferingService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("pageOfferings PageResult 被 Result 包装")
  void pageOfferings_isWrapped() throws Exception {
    CourseOfferingListItemVO vo = new CourseOfferingListItemVO();
    vo.setCourseOfferingId(1L);
    vo.setOfferingCode("OFF-001");
    when(courseOfferingService.pageOfferings(any())).thenReturn(new PageResult<>(List.of(vo), 1L));

    mockMvc.perform(get("/course/offerings"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.list[0].offeringCode").value("OFF-001"));
  }

  @Test
  @DisplayName("getOffering 详情被 Result 包装")
  void getOffering_isWrapped() throws Exception {
    CourseOfferingVO vo = new CourseOfferingVO();
    vo.setCourseOfferingId(1L);
    vo.setOfferingName("2026春季班");
    when(courseOfferingService.getOffering(1L)).thenReturn(vo);

    mockMvc.perform(get("/course/offerings/1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.offeringName").value("2026春季班"));
  }

  @Test
  @DisplayName("createOffering 业务异常 → 409")
  void createOffering_businessException_yields409() throws Exception {
    when(courseOfferingService.createOffering(any()))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 5070, "开课编码已存在"));

    mockMvc.perform(post("/course/offerings")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"offeringCode":"OFF-001","courseId":10,"offeringName":"春季班",
                                 "courseType":"REQUIRED","enrollmentType":"ASSIGNED"}
                                 """))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(5070));
  }
}

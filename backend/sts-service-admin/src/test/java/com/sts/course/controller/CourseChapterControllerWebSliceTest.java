package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseChapterService;
import com.sts.course.vo.CourseChapterVO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CourseChapterController} Web 切片测试。
 */
class CourseChapterControllerWebSliceTest {

  private CourseChapterService chapterService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    chapterService = mock(CourseChapterService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseChapterController(chapterService))
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("裸列表返回被包成 Result")
  void listChapters_isWrapped() throws Exception {
    CourseChapterVO vo = new CourseChapterVO();
    vo.setChapterId(1L);
    vo.setChapterName("第一章");
    when(chapterService.listChapters(1L)).thenReturn(List.of(vo));

    mockMvc.perform(get("/course/chapters").param("courseId", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data[0].chapterName").value("第一章"));
  }

  @Test
  @DisplayName("删除最后一个章节 → 409 / 5023")
  void deleteChapter_lastRemaining_yields409() throws Exception {
    doThrow(new BusinessException(HttpStatus.CONFLICT, 5023, "课程至少保留一个章节，不能删除最后一个"))
      .when(chapterService).deleteChapter(1L);

    mockMvc.perform(delete("/course/chapters/1"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(5023));
  }

  @Test
  @DisplayName("@Valid 失败（章节名为空）→ 400 / code=1001，不进 service")
  void validationFailure_returns400() throws Exception {
    mockMvc.perform(post("/course/chapters")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"courseId\":1,\"chapterName\":\"\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(1001));

    verify(chapterService, never()).createChapter(any());
  }
}

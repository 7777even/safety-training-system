package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseItemService;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseItemControllerWebSliceTest {

  private CourseItemService itemService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    itemService = mock(CourseItemService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseItemController(itemService))
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("禁用类型创建 → 400 / 5032")
  void create_disabledType_yields5032() throws Exception {
    doThrow(new BusinessException(HttpStatus.BAD_REQUEST, 5032, "该内容项类型暂未开放"))
      .when(itemService).create(any());

    mockMvc.perform(post("/course/items")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"chapterId":1,"itemType":"EXAM","itemTitle":"考试","isRequired":1,
                                 "minimumStudySeconds":60,"sortOrder":0,"status":1}
                                 """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(5032));
  }

  @Test
  @DisplayName("创建成功返回被 Result 包装的新 ID")
  void create_success_isWrapped() throws Exception {
    when(itemService.create(any())).thenReturn(42L);

    mockMvc.perform(post("/course/items")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"chapterId":1,"itemType":"ASSET","itemTitle":"文档","assetId":100,
                                 "isRequired":1,"minimumStudySeconds":60,"sortOrder":0,"status":1}
                                 """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data").value(42));
  }
}

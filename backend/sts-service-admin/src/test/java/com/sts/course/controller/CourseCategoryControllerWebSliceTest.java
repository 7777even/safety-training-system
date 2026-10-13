package com.sts.course.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseCategoryService;
import com.sts.course.vo.CourseCategoryTreeVO;
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
 * {@link CourseCategoryController} Web 切片测试：挂真实的 {@link HttpApiExceptionHandler}
 * + {@link ResultEasyTransAdvice}，验证 Result 包装、失败真实 HTTP 状态、鉴权异常映射、{@code @Valid} 校验。
 * 与 {@code SysUserControllerWebSliceTest} 同一套模式（见 test-strategy.md §2.5）。
 */
class CourseCategoryControllerWebSliceTest {

  private CourseCategoryService categoryService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    categoryService = mock(CourseCategoryService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseCategoryController(categoryService))
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("裸列表返回被包成 Result：code=0 / data 为真实列表")
  void tree_isWrappedIntoResult() throws Exception {
    CourseCategoryTreeVO vo = new CourseCategoryTreeVO();
    vo.setCourseCategoryId(1L);
    vo.setCategoryName("安全生产");
    when(categoryService.getCategoryTree(null, null)).thenReturn(List.of(vo));

    mockMvc.perform(get("/course/categories/tree"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data[0].categoryName").value("安全生产"));
  }

  @Test
  @DisplayName("Service 抛 BusinessException(409) → 响应真为 409，body 为 Result.fail(业务码)")
  void businessException_yieldsRealHttpStatus() throws Exception {
    when(categoryService.createCategory(any()))
      .thenThrow(new BusinessException(HttpStatus.CONFLICT, 5005, "同层分类名称已存在"));

    mockMvc.perform(post("/course/categories")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"parentId\":0,\"categoryName\":\"安全生产\"}"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value(5005))
      .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  @DisplayName("@Valid 失败 → 400 且 body 为 Result.fail(1001)，不进 service")
  void validationFailure_returns400() throws Exception {
    mockMvc.perform(post("/course/categories")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"parentId\":0,\"categoryName\":\"\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(1001));

    verify(categoryService, never()).createCategory(any());
  }

  @Test
  @DisplayName("未登录异常 → 401 / code=1002")
  void notLoginException_maps401() throws Exception {
    when(categoryService.getCategoryTree(any(), any())).thenThrow(
      NotLoginException.newInstance("login", NotLoginException.NOT_TOKEN,
                                    NotLoginException.NOT_TOKEN_MESSAGE, null));

    mockMvc.perform(get("/course/categories/tree"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value(1002));
  }

  @Test
  @DisplayName("权限不足异常 → 403 / code=1003")
  void notPermissionException_maps403() throws Exception {
    doThrow(new NotPermissionException("course:category:delete"))
      .when(categoryService).deleteCategory(9L);

    mockMvc.perform(delete("/course/categories/9"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value(1003));
  }
}

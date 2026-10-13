package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseAssetReferenceService;
import com.sts.course.vo.CourseAssetReferenceVO;
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

class CourseAssetReferenceControllerWebSliceTest {

  private CourseAssetReferenceService assetReferenceService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    assetReferenceService = mock(CourseAssetReferenceService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseAssetReferenceController(assetReferenceService))
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("getReferences 引用统计被 Result 包装")
  void getReferences_isWrapped() throws Exception {
    CourseAssetReferenceVO vo = new CourseAssetReferenceVO();
    vo.setAssetId(100L);
    vo.setItemCount(3L);
    vo.setDownloadCount(2L);
    vo.setReferenceCount(5L);
    when(assetReferenceService.getReferences(100L)).thenReturn(vo);

    mockMvc.perform(get("/course/asset-references").param("assetId", "100"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.referenceCount").value(5))
      .andExpect(jsonPath("$.data.itemCount").value(3));
  }

  @Test
  @DisplayName("getReferences 业务异常 → 404 / 6011")
  void getReferences_businessException_yields404() throws Exception {
    when(assetReferenceService.getReferences(999L))
      .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, 6011, "素材不存在"));

    mockMvc.perform(get("/course/asset-references").param("assetId", "999"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(6011));
  }
}

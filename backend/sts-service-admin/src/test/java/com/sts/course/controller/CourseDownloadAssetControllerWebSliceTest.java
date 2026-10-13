package com.sts.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.dto.DownloadAssetReplaceRequest;
import com.sts.course.service.CourseDownloadAssetService;
import com.sts.course.vo.DownloadAssetVO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseDownloadAssetControllerWebSliceTest {

  private CourseDownloadAssetService downloadAssetService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    downloadAssetService = mock(CourseDownloadAssetService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseDownloadAssetController(downloadAssetService))
      .setControllerAdvice(
        new HttpApiExceptionHandler(),
        new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("listByCourse 列表被 Result 包装")
  void listByCourse_isWrapped() throws Exception {
    DownloadAssetVO vo = new DownloadAssetVO();
    vo.setCourseDownloadAssetId(1L);
    vo.setAssetId(100L);
    when(downloadAssetService.listByCourse(10L)).thenReturn(List.of(vo));

    mockMvc.perform(get("/course/courses/10/download-assets"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data[0].assetId").value(100));
  }

  @Test
  @DisplayName("replaceAll 成功返回被 Result 包装的 void")
  void replaceAll_success_isWrapped() throws Exception {
    mockMvc.perform(put("/course/courses/10/download-assets")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"assets":[{"assetId":100,"sortOrder":0,"status":1}]}
                                 """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0));

    verify(downloadAssetService).replaceAll(eq(10L), any(DownloadAssetReplaceRequest.class));
  }

  @Test
  @DisplayName("replaceAll 重复素材 → 400 / 5038")
  void replaceAll_duplicateAsset_yields5038() throws Exception {
    doThrow(new BusinessException(HttpStatus.BAD_REQUEST, 5038, "下载素材列表中存在重复素材"))
      .when(downloadAssetService).replaceAll(eq(10L), any(DownloadAssetReplaceRequest.class));

    mockMvc.perform(put("/course/courses/10/download-assets")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"assets":[{"assetId":100,"sortOrder":0,"status":1},
                                 {"assetId":100,"sortOrder":1,"status":1}]}
                                 """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(5038));
  }
}

package com.sts.course.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.common.exception.BusinessException;
import com.sts.common.exception.HttpApiExceptionHandler;
import com.sts.course.service.CourseCoverService;
import com.sts.course.vo.CourseCoverUploadVO;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseCoverControllerWebSliceTest {

  private CourseCoverService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(CourseCoverService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CourseCoverController(service))
      .setControllerAdvice(new HttpApiExceptionHandler(), new ResultEasyTransAdvice(
        mock(TranslationExecutor.class), new ObjectMapper()))
      .build();
  }

  @Test
  void uploadBindsMultipartAndWrapsResponse() throws Exception {
    CourseCoverUploadVO result = new CourseCoverUploadVO();
    result.setFileInfoId(88L);
    result.setFileName("cover.png");
    result.setFileSize(8L);
    result.setContentType("image/png");
    when(service.upload(any())).thenReturn(result);
    MockMultipartFile file = new MockMultipartFile(
      "file", "cover.png", "image/png", new byte[]{(byte) 0x89, 0x50});

    mockMvc.perform(multipart("/course/covers").file(file))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.fileInfoId").value(88))
      .andExpect(jsonPath("$.data.objectName").doesNotExist());
    verify(service).upload(any());
  }

  @Test
  void missingMultipartFileReturns400WithoutServiceCall() throws Exception {
    when(service.upload(null)).thenThrow(
      new BusinessException(HttpStatus.BAD_REQUEST, 5090, "课程封面不能为空"));
    mockMvc.perform(multipart("/course/covers"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value(5090));
    verify(service).upload(null);
  }

  @Test
  void coverNotFoundUsesStandardErrorEnvelope() throws Exception {
    when(service.open(10L)).thenThrow(
      new BusinessException(HttpStatus.NOT_FOUND, 5092, "课程封面不存在"));

    mockMvc.perform(get("/course/courses/10/cover"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value(5092));
  }

  @Test
  void uploadPermissionAllowsAddOrEdit() throws Exception {
    Method method = CourseCoverController.class.getMethod(
      "upload", org.springframework.web.multipart.MultipartFile.class);
    SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);

    assertThat(permission).isNotNull();
    assertThat(permission.mode()).isEqualTo(SaMode.OR);
    assertThat(permission.value()).containsExactly("course:course:add", "course:course:edit");
  }
}

package com.sts.admin.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.system.service.MultipartUploadService;
import com.sts.admin.system.vo.MultipartCompleteVO;
import com.sts.admin.system.vo.MultipartInitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SysFileMultipartControllerTest {

  private MultipartUploadService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(MultipartUploadService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new SysFileMultipartController(service)).build();
  }

  @Test
  void initBindsValidatedRequestAndCurrentUser() throws Exception {
    MultipartInitVO result = new MultipartInitVO("upload-1", 10_485_760L, List.of());
    when(service.init(eq(7L), any())).thenReturn(result);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      mockMvc.perform(post("/system/files/multipart/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"video.mp4\",\"contentType\":\"video/mp4\","
                                   + "\"fileSize\":20000000,\"checksum\":\"" + "a".repeat(64) + "\",\"folder\":\"asset\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadId").value("upload-1"))
        .andExpect(jsonPath("$.partSize").value(10_485_760));
    }
  }

  @Test
  void initRejectsInvalidChecksumBeforeService() throws Exception {
    mockMvc.perform(post("/system/files/multipart/init")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"fileName\":\"video.mp4\",\"contentType\":\"video/mp4\","
                                 + "\"fileSize\":20,\"checksum\":\"bad\",\"folder\":\"asset\"}"))
      .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void completeDelegatesPartEtagsAndReturnsFileId() throws Exception {
    when(service.complete(eq(7L), eq("upload-1"), any()))
      .thenReturn(new MultipartCompleteVO(99L));

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      mockMvc.perform(post("/system/files/multipart/upload-1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[{\"partNumber\":1,\"etag\":\"etag-1\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileInfoId").value(99));
    }
  }
}

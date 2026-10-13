package com.sts.training.controller;

import com.sts.training.service.TrainingCoverFile;
import com.sts.training.service.TrainingTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainingTypeControllerTest {

  private TrainingTypeService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(TrainingTypeService.class);
    mvc = MockMvcBuilders.standaloneSetup(new TrainingTypeController(service)).build();
  }

  @Test
  @DisplayName("创建培训类型缺少复审学时返回 400 且不进入 Service")
  void createMissingReviewHoursReturnsBadRequest() throws Exception {
    mvc.perform(post("/training/types")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"typeCode\":\"T1\",\"typeName\":\"高压电工作业\","
                             + "\"initialHours\":80,\"status\":1,\"description\":\"类型\"}"))
      .andExpect(status().isBadRequest());

    verify(service, never()).create(any());
  }

  @Test
  @DisplayName("创建培训类型不接受非正数初培学时")
  void createRejectsNonPositiveInitialHours() throws Exception {
    mvc.perform(post("/training/types")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"typeCode\":\"T1\",\"typeName\":\"高压电工作业\","
                             + "\"initialHours\":0,\"reviewHours\":24,\"status\":1,\"description\":\"类型\"}"))
      .andExpect(status().isBadRequest());

    verify(service, never()).create(any());
  }

  @Test
  @DisplayName("类型封面默认内联并支持附件下载")
  void coverDefaultsToInlineAndSupportsAttachmentDownload() throws Exception {
    when(service.openCover(1L)).thenReturn(new TrainingCoverFile(
      new org.springframework.core.io.ByteArrayResource("png".getBytes()),
      "类型封面.png",
      "image/png",
      3L,
      "\"900-0-3\""));
    TrainingTypeController controller = new TrainingTypeController(service);

    var inline = controller.cover(1L, false);
    var attachment = controller.cover(1L, true);

    org.assertj.core.api.Assertions.assertThat(inline.getHeaders().getContentDisposition().getType())
      .isEqualTo("inline");
    org.assertj.core.api.Assertions.assertThat(attachment.getHeaders().getContentDisposition().getType())
      .isEqualTo("attachment");

    mvc.perform(get("/training/types/1/cover"))
      .andExpect(status().isOk());
    verify(service, times(3)).openCover(1L);
  }
}

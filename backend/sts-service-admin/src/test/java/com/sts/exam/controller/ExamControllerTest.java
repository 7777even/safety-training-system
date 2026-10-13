package com.sts.exam.controller;

import com.sts.exam.dto.ExamReviewersReplaceRequest;
import com.sts.exam.service.ExamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamControllerTest {

  private ExamService examService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    examService = mock(ExamService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new ExamController(examService, mock(com.sts.exam.service.OfflineScoreService.class))).build();
  }

  @Test
  @DisplayName("PUT reviewers：空数组是合法的整体清空请求")
  void replaceReviewers_acceptsEmptyArray() throws Exception {
    mockMvc.perform(put("/exam/exams/1/reviewers")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"reviewerUserIds\":[]}"))
      .andExpect(status().isOk());

    ArgumentCaptor<ExamReviewersReplaceRequest> captor = ArgumentCaptor.forClass(ExamReviewersReplaceRequest.class);
    verify(examService).replaceReviewers(eq(1L), captor.capture());
    assertThat(captor.getValue().getReviewerUserIds()).isEmpty();
  }

  @Test
  @DisplayName("PUT reviewers：缺少 reviewerUserIds 返回 400 且不进入 Service")
  void replaceReviewers_rejectsMissingArray() throws Exception {
    mockMvc.perform(put("/exam/exams/1/reviewers")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
      .andExpect(status().isBadRequest());

    verifyNoInteractions(examService);
  }
}

package com.sts.exam.controller;

import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.common.api.PageResult;
import com.sts.exam.service.ExamAttemptQueryService;
import com.sts.exam.service.ExamAttemptService;
import com.sts.exam.vo.ExamAttemptVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamAttemptListControllerTest {

  @Test
  void pageReviewTasks_bindsPageAndQuery() throws Exception {
    ExamAttemptQueryService service = mock(ExamAttemptQueryService.class);
    when(service.pageReviewTasks(org.mockito.ArgumentMatchers.any())).thenReturn(new PageResult<>(List.of(), 0));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExamAttemptController(null, service, null))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver()).build();

    mvc.perform(get("/exam/review-tasks")
                  .param("pageNum", "2")
                  .param("pageSize", "10")
                  .param("query.examId", "12")
                  .param("query.reviewStatus", "PENDING"))
      .andExpect(status().isOk());

    verify(service).pageReviewTasks(argThat(request -> request.getPageNum() == 2
      && request.getPageSize() == 10 && request.getQuery().getExamId().equals(12L)
      && "PENDING".equals(request.getQuery().getReviewStatus())));
  }

  @Test
  void pageAttempts_bindsPublishStatus() throws Exception {
    ExamAttemptQueryService service = mock(ExamAttemptQueryService.class);
    when(service.pageAttempts(org.mockito.ArgumentMatchers.any())).thenReturn(new PageResult<>(List.of(), 0));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExamAttemptController(null, service, null))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver()).build();

    mvc.perform(get("/exam/attempts")
                  .param("query.examId", "12")
                  .param("query.scorePublishStatus", "UNPUBLISHED"))
      .andExpect(status().isOk());

    verify(service).pageAttempts(argThat(request -> request.getQuery().getExamId().equals(12L)
      && "UNPUBLISHED".equals(request.getQuery().getScorePublishStatus())));
  }

  @Test
  void retake_passesCandidateIdAndIdempotencyToken() throws Exception {
    ExamAttemptService attemptService = mock(ExamAttemptService.class);
    when(attemptService.retake(20L, "tok-retake-1")).thenReturn(new ExamAttemptVO());
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExamAttemptController(attemptService, null, null)).build();

    mvc.perform(post("/exam/candidates/20/retake").param("clientAttemptToken", "tok-retake-1"))
      .andExpect(status().isOk());

    verify(attemptService).retake(20L, "tok-retake-1");
  }
}

package com.sts.exam.controller;

import com.sts.common.api.PageResult;
import com.sts.exam.service.ExamLearnerService;
import com.sts.exam.vo.ExamAttemptVO;
import com.sts.exam.vo.ExamLearnerTaskVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamLearnerControllerTest {

  @Test
  void pageMyTasks_bindsPageAndQueryConditions() throws Exception {
    ExamLearnerService service = mock(ExamLearnerService.class);
    when(service.pageMyTasks(any())).thenReturn(new PageResult<ExamLearnerTaskVO>(List.of(), 0));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExamLearnerController(service)).build();

    mvc.perform(get("/exam/learner/tasks")
                  .param("pageNum", "2")
                  .param("pageSize", "10")
                  .param("query.taskStatus", "IN_PROGRESS"))
      .andExpect(status().isOk());

    verify(service).pageMyTasks(any());
  }

  @Test
  void getMyAttempt_delegatesPathId() throws Exception {
    ExamLearnerService service = mock(ExamLearnerService.class);
    when(service.getMyAttempt(40L)).thenReturn(new ExamAttemptVO());
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExamLearnerController(service)).build();

    mvc.perform(get("/exam/learner/attempts/40"))
      .andExpect(status().isOk());

    verify(service).getMyAttempt(40L);
  }

  @Test
  void coverDefaultsToInlineAndSupportsAttachmentDownload() throws Exception {
    ExamLearnerService service = mock(ExamLearnerService.class);
    when(service.openCover(1L)).thenReturn(new ExamLearnerService.ExamCoverFile(
      new org.springframework.core.io.ByteArrayResource("png".getBytes()),
      "考试封面.png",
      "image/png",
      3L,
      "\"900-0-3\""));
    ExamLearnerController controller = new ExamLearnerController(service);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

    var inline = controller.cover(1L, false);
    var attachment = controller.cover(1L, true);

    org.assertj.core.api.Assertions.assertThat(inline.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
    org.assertj.core.api.Assertions.assertThat(attachment.getHeaders().getContentDisposition().getType()).isEqualTo(
      "attachment");
    org.assertj.core.api.Assertions.assertThat(inline.getHeaders().getContentType().toString()).isEqualTo("image/png");
    org.assertj.core.api.Assertions.assertThat(inline.getHeaders().getETag()).isEqualTo("\"900-0-3\"");
    org.assertj.core.api.Assertions.assertThat(inline.getHeaders().getContentLength()).isEqualTo(3L);

    mvc.perform(get("/exam/learner/exams/1/cover"))
      .andExpect(status().isOk());
    verify(service, times(3)).openCover(1L);
  }
}

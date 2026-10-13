package com.sts.training.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.training.service.TrainingAttachmentFile;
import com.sts.training.service.TrainingCoverFile;
import com.sts.training.service.TrainingWorkflowService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainingWorkflowControllerTest {

  @Test
  void teacherDetail_usesCurrentLoginIdentity() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(77L);

      mvc.perform(get("/training/teacher-tasks/9"))
        .andExpect(status().isOk());

      verify(service).getTeacherTask(9L, 77L);
    }
  }

  @Test
  void learnerTasks_usesCurrentLoginIdentity() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);

      mvc.perform(get("/training/learner/tasks"))
        .andExpect(status().isOk());

      verify(service).listLearnerTasks(88L);
    }
  }

  @Test
  void learnerPlanCover_usesCurrentLoginIdentity() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    when(service.openLearnerPlanCover(20L, 88L)).thenReturn(new TrainingCoverFile(
      new org.springframework.core.io.ByteArrayResource("png".getBytes()),
      "计划封面.png",
      "image/png",
      3L,
      "\"900-0-3\""));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);

      mvc.perform(get("/training/learner/plans/20/cover"))
        .andExpect(status().isOk());

      verify(service).openLearnerPlanCover(20L, 88L);
    }
  }

  @Test
  void enter_usesCurrentLoginIdentityWithoutClientIdentityFields() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);

      mvc.perform(post("/training/learner/participants/2/contents/3/enter")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"clientAttemptToken\":\"tok\"}"))
        .andExpect(status().isOk());

      verify(service).enter(eq(2L), eq(3L), any(), eq(88L));
    }
  }

  @Test
  void learnerOfflineSession_usesCurrentLoginIdentity() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);

      mvc.perform(get("/training/learner/offline-sessions/301"))
        .andExpect(status().isOk());

      verify(service).getLearnerOfflineSession(301L, 88L);
    }
  }

  @Test
  void endedResult_delegatesToService() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();
    mvc.perform(get("/training/plans/10/ended-result")).andExpect(status().isOk());
    verify(service).getEndedResult(10L);
  }

  /** 学员资料清单的主体必须取自登录态，客户端不得传入 userId 覆盖。 */
  @Test
  void learnerMaterials_usesCurrentLoginIdentity() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);

      mvc.perform(get("/training/learner/participants/2/materials"))
        .andExpect(status().isOk());

      verify(service).listLearnerMaterials(2L, 88L);
    }
  }

  /** 学员资料文件只按内联返回，响应不得携带 attachment 下载语义。 */
  @Test
  void learnerMaterialFile_inlinesContentWithoutDownload() throws Exception {
    TrainingWorkflowService service = mock(TrainingWorkflowService.class);
    when(service.openLearnerMaterialFile(2L, 501L, 88L)).thenReturn(new TrainingAttachmentFile(
      new org.springframework.core.io.ByteArrayResource("pdf".getBytes()), "制度.pdf", 3L));
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingWorkflowController(service)).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);

      mvc.perform(get("/training/learner/participants/2/materials/501/file"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", containsString("inline")));

      verify(service).openLearnerMaterialFile(2L, 501L, 88L);
    }
  }
}

package com.sts.training.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.training.dto.TrainingRewardPenaltyCreateRequest;
import com.sts.training.service.TrainingAttachmentFile;
import com.sts.training.service.TrainingAttachmentService;
import com.sts.training.service.TrainingOfflineScoreService;
import com.sts.training.service.TrainingOfflineSessionService;
import com.sts.training.service.TrainingRewardEvidenceFile;
import com.sts.training.service.TrainingRewardPenaltyService;
import com.sts.training.service.TrainingTeacherTaskService;
import org.springframework.core.io.ByteArrayResource;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainingExecutionControllerTest {

  @Test
  void rewardEvidence_returnsInlineFileFromBoundRecord() throws Exception {
    TrainingRewardPenaltyService rewardService = mock(TrainingRewardPenaltyService.class);
    when(rewardService.openEvidence(9L)).thenReturn(new TrainingRewardEvidenceFile(
      new ByteArrayResource(new byte[] {1, 2}), "奖惩证明.pdf", 2));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrainingExecutionController(
      mock(TrainingOfflineSessionService.class), mock(TrainingOfflineScoreService.class),
      mock(TrainingAttachmentService.class), rewardService, mock(TrainingTeacherTaskService.class))).build();

    mockMvc.perform(get("/training/reward-penalties/9/evidence"))
      .andExpect(status().isOk())
      .andExpect(header().string("Content-Type", "application/pdf"))
      .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
      .andExpect(content().bytes(new byte[] {1, 2}));
    verify(rewardService).openEvidence(9L);
  }

  @Test
  void rewardEvidence_doesNotInlineActiveHtml() throws Exception {
    TrainingRewardPenaltyService rewardService = mock(TrainingRewardPenaltyService.class);
    when(rewardService.openEvidence(9L)).thenReturn(new TrainingRewardEvidenceFile(
      new ByteArrayResource("<script></script>".getBytes()), "证明.html", 17));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrainingExecutionController(
      mock(TrainingOfflineSessionService.class), mock(TrainingOfflineScoreService.class),
      mock(TrainingAttachmentService.class), rewardService, mock(TrainingTeacherTaskService.class))).build();

    mockMvc.perform(get("/training/reward-penalties/9/evidence"))
      .andExpect(status().isOk())
      .andExpect(header().string("Content-Type", "application/octet-stream"))
      .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  @Test
  void createRewardPenalty_acceptsPlanParticipantAndSignedScore() throws Exception {
    TrainingRewardPenaltyService rewardService = mock(TrainingRewardPenaltyService.class);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrainingExecutionController(
      mock(TrainingOfflineSessionService.class), mock(TrainingOfflineScoreService.class),
      mock(TrainingAttachmentService.class), rewardService, mock(TrainingTeacherTaskService.class))).build();
    String body = """
      {"trainingPlanId":10,"trainingParticipantId":20,"recordType":"PENALTY",
       "title":"违反培训纪律","scoreChange":-2,"status":1}
      """;

    mockMvc.perform(post("/training/reward-penalties")
                      .contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk());

    org.mockito.ArgumentCaptor<TrainingRewardPenaltyCreateRequest> payload =
      org.mockito.ArgumentCaptor.forClass(TrainingRewardPenaltyCreateRequest.class);
    verify(rewardService).create(payload.capture());
    org.assertj.core.api.Assertions.assertThat(payload.getValue().getTrainingParticipantId()).isEqualTo(20L);
    org.assertj.core.api.Assertions.assertThat(payload.getValue().getScoreChange())
      .isEqualByComparingTo("-2");
  }

  @Test
  void upsertOfflineScore_usesCurrentLoginUserAsScorer() throws Exception {
    TrainingOfflineScoreService scoreService = mock(TrainingOfflineScoreService.class);
    MockMvc mockMvc = mockMvc(scoreService);
    String body = """
      {
        "organizationId": 1,
        "trainingParticipantId": 2,
        "offlineSessionId": 3,
        "scoreName": "线下实操",
        "score": 90,
        "maxScore": 100,
        "scoreSource": "MANUAL",
        "scoredTime": "2026-07-31T10:00:00"
      }
      """;

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(88L);

      mockMvc.perform(post("/training/offline-scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
        .andExpect(status().isOk());

      verify(scoreService).upsert(any(), eq(88L), eq(false));
    }
  }

  @Test
  void startSession_usesCurrentLoginAndPlanEditFlag() throws Exception {
    TrainingOfflineSessionService sessionService = mock(TrainingOfflineSessionService.class);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrainingExecutionController(
      sessionService,
      mock(TrainingOfflineScoreService.class),
      mock(TrainingAttachmentService.class),
      mock(TrainingRewardPenaltyService.class),
      mock(TrainingTeacherTaskService.class))).build();

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
      stp.when(() -> StpUtil.hasPermission("training:plan:edit")).thenReturn(true);

      mockMvc.perform(post("/training/offline-sessions/9/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
        .andExpect(status().isOk());

      verify(sessionService).start(9L, 1, 7L, true);
    }
  }

  @Test
  void attachmentFile_returnsInlineFileFromBoundRecord() throws Exception {
    TrainingAttachmentService attachmentService = mock(TrainingAttachmentService.class);
    when(attachmentService.openFile(9L)).thenReturn(new TrainingAttachmentFile(
      new ByteArrayResource(new byte[] {1, 2}), "现场照片.png", 2));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrainingExecutionController(
      mock(TrainingOfflineSessionService.class), mock(TrainingOfflineScoreService.class),
      attachmentService, mock(TrainingRewardPenaltyService.class), mock(TrainingTeacherTaskService.class))).build();

    mockMvc.perform(get("/training/attachments/9/file"))
      .andExpect(status().isOk())
      .andExpect(header().string("Content-Type", "image/png"))
      .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
      .andExpect(content().bytes(new byte[] {1, 2}));
    verify(attachmentService).openFile(9L);
  }

  @Test
  void attachmentFile_doesNotInlineActiveHtml() throws Exception {
    TrainingAttachmentService attachmentService = mock(TrainingAttachmentService.class);
    when(attachmentService.openFile(9L)).thenReturn(new TrainingAttachmentFile(
      new ByteArrayResource("<script></script>".getBytes()), "资料.html", 17));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrainingExecutionController(
      mock(TrainingOfflineSessionService.class), mock(TrainingOfflineScoreService.class),
      attachmentService, mock(TrainingRewardPenaltyService.class), mock(TrainingTeacherTaskService.class))).build();

    mockMvc.perform(get("/training/attachments/9/file"))
      .andExpect(status().isOk())
      .andExpect(header().string("Content-Type", "application/octet-stream"))
      .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  private MockMvc mockMvc(TrainingOfflineScoreService scoreService) {    TrainingExecutionController controller = new TrainingExecutionController(
      mock(TrainingOfflineSessionService.class),
      scoreService,
      mock(TrainingAttachmentService.class),
      mock(TrainingRewardPenaltyService.class),
      mock(TrainingTeacherTaskService.class));
    return MockMvcBuilders.standaloneSetup(controller).build();
  }
}

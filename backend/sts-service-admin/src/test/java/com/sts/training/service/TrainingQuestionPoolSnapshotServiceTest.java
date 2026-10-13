package com.sts.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.question.draw.QuestionDrawEngine;
import com.sts.question.entity.QuestionBank;
import com.sts.training.dto.TrainingContentExecutionConfig;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanContent;
import com.sts.training.entity.TrainingPlanQuestionPool;
import com.sts.training.mapper.TrainingPlanQuestionPoolMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingQuestionPoolSnapshotServiceTest {

  @Test
  void freezesStableVersionPoolAndReadsItBack() throws Exception {
    TrainingPlanQuestionPoolMapper mapper = mock(TrainingPlanQuestionPoolMapper.class);
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    TrainingQuestionPoolSnapshotService service = new TrainingQuestionPoolSnapshotService(mapper, objectMapper);
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(1L);
    plan.setOrganizationId(2L);
    TrainingPlanContent content = new TrainingPlanContent();
    content.setPlanContentId(3L);
    content.setResourceId(4L);
    QuestionBank bank = new QuestionBank();
    bank.setQuestionBankId(4L);
    bank.setBankCode("B-1");
    bank.setBankName("安全题库");
    List<QuestionDrawEngine.Candidate> candidates = List.of(
      new QuestionDrawEngine.Candidate(11L, 101L, "SINGLE", "EASY", Set.of(8L),
                                       QuestionDrawEngine.EvaluationMode.AUTO),
      new QuestionDrawEngine.Candidate(12L, 102L, "ESSAY", "NORMAL", Set.of(),
                                       QuestionDrawEngine.EvaluationMode.RECORD_ONLY));

    TrainingQuestionPoolSnapshotService.FrozenPool frozen =
      service.freeze(plan, content, bank, new TrainingContentExecutionConfig(), candidates);

    ArgumentCaptor<TrainingPlanQuestionPool> rows = ArgumentCaptor.forClass(TrainingPlanQuestionPool.class);
    verify(mapper, times(2)).insert(rows.capture());
    assertThat(rows.getAllValues()).extracting(TrainingPlanQuestionPool::getQuestionVersionId)
      .containsExactly(101L, 102L);
    assertThat(frozen.poolHash()).hasSize(64);
    assertThat(content.getResourceSnapshotJson()).contains("\"schemaVersion\":2").contains("\"questionVersionId\":101");
    assertThat(content.getResourceSnapshotHash()).hasSize(64);

    when(mapper.selectList(any())).thenReturn(rows.getAllValues());
    // MySQL JSON 会重排对象键，读取完整性校验必须基于规范化 JSON，而不是原始字符串字节。
    content.setResourceSnapshotJson(objectMapper.writeValueAsString(
      objectMapper.readValue(content.getResourceSnapshotJson(), java.util.TreeMap.class)));
    TrainingQuestionPoolSnapshotService.FrozenPool loaded = service.load(content);
    assertThat(loaded.poolHash()).isEqualTo(frozen.poolHash());
    assertThat(loaded.items()).hasSize(2);
  }
}

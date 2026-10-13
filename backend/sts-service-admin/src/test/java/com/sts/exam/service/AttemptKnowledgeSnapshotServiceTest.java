package com.sts.exam.service;
import com.sts.exam.mapper.AssessmentExecutionQuestionPoolMapper;
import com.sts.exam.entity.AssessmentExecutionQuestionPool;
import com.sts.question.mapper.QuestionKnowledgeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
/** 冻结题池优先于当前关联，未关联用空数组。 */
class AttemptKnowledgeSnapshotServiceTest {
  @Test void newerQuestionVersionCapturesItsOwnKnowledgeAtFreeze() {
    var pool=mock(AssessmentExecutionQuestionPoolMapper.class);var relations=mock(QuestionKnowledgeMapper.class);var executions=mock(com.sts.exam.mapper.AssessmentExecutionMapper.class);
    var execution=new com.sts.exam.entity.AssessmentExecution();execution.setPolicySnapshotJson("{\"knowledgePointSnapshots\":[{\"questionId\":2,\"questionVersionId\":3,\"knowledgePointIds\":[7]}]}");execution.setOrganizationId(1L);execution.setSourceType("COURSE_QUIZ");execution.setSourceBusinessId(5L);execution.setAssessmentKind("QUIZ");execution.setResourceType("PAPER_VERSION");execution.setResourceId(31L);execution.setResourceNameSnapshot("试卷");execution.setDurationSecondsSnapshot(60);new com.sts.exam.service.AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);execution.setPolicySnapshotJson(null);when(executions.selectById(1L)).thenReturn(execution);
    var rel=new com.sts.question.entity.QuestionKnowledge();rel.setKnowledgePointId(8L);when(relations.selectList(any())).thenReturn(List.of(rel));
    assertThat(new AttemptKnowledgeSnapshotService(pool,relations,new ObjectMapper(),executions).snapshot(1L,2L,4L)).isEqualTo("[8]");verify(relations).selectList(any());
  }
  @Test void courseFrozenRuleDoesNotReadCurrentRelations() {
    var pool=mock(AssessmentExecutionQuestionPoolMapper.class);var relations=mock(QuestionKnowledgeMapper.class);var executions=mock(com.sts.exam.mapper.AssessmentExecutionMapper.class);
    var execution=new com.sts.exam.entity.AssessmentExecution();execution.setPolicySnapshotJson("{\"knowledgePointSnapshots\":[{\"questionId\":2,\"questionVersionId\":4,\"knowledgePointIds\":[7]}]}");execution.setOrganizationId(1L);execution.setSourceType("COURSE_QUIZ");execution.setSourceBusinessId(5L);execution.setAssessmentKind("QUIZ");execution.setResourceType("PAPER_VERSION");execution.setResourceId(31L);execution.setResourceNameSnapshot("试卷");execution.setDurationSecondsSnapshot(60);new com.sts.exam.service.AssessmentExecutionSnapshotCodec(new ObjectMapper()).encode(execution);execution.setPolicySnapshotJson(null);
    when(executions.selectById(1L)).thenReturn(execution);
    assertThat(new AttemptKnowledgeSnapshotService(pool,relations,new ObjectMapper(),executions).snapshot(1L,2L,4L)).isEqualTo("[7]");verifyNoInteractions(relations);
  }
  @Test void frozenPoolDoesNotReadCurrentQuestionRelations() {
    var pool=mock(AssessmentExecutionQuestionPoolMapper.class);var relations=mock(QuestionKnowledgeMapper.class);
    var row=new AssessmentExecutionQuestionPool();row.setKnowledgePointIdsJson("[7]");
    when(pool.selectList(any())).thenReturn(List.of(row));
    assertThat(new AttemptKnowledgeSnapshotService(pool,relations,new ObjectMapper(),mock(com.sts.exam.mapper.AssessmentExecutionMapper.class)).snapshot(1L,2L,4L)).isEqualTo("[7]");
    verifyNoInteractions(relations);
  }
  @Test void newUnassociatedQuestionIsKnownEmptyRatherThanHistoricalUnknown() {
    var pool=mock(AssessmentExecutionQuestionPoolMapper.class);var relations=mock(QuestionKnowledgeMapper.class);
    when(pool.selectList(any())).thenReturn(List.of());when(relations.selectList(any())).thenReturn(List.of());
    assertThat(new AttemptKnowledgeSnapshotService(pool,relations,new ObjectMapper(),mock(com.sts.exam.mapper.AssessmentExecutionMapper.class)).snapshot(1L,2L,4L)).isEqualTo("[]");
  }
}

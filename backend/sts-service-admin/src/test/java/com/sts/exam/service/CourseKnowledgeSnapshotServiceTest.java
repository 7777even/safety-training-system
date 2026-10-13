package com.sts.exam.service;
import com.sts.paper.entity.PaperVersionQuestion;
import com.sts.paper.mapper.PaperVersionQuestionMapper;
import com.sts.question.entity.QuestionKnowledge;
import com.sts.question.mapper.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
/** 新开班冻结试卷知识点；无需学员作答即可统计覆盖。 */
class CourseKnowledgeSnapshotServiceTest {
  @Test void readsPersistedExecutionRatherThanTransientPolicyView() {
    var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
    var execution=new com.sts.exam.entity.AssessmentExecution();execution.setOrganizationId(1L);execution.setSourceType("COURSE_QUIZ");execution.setSourceBusinessId(5L);execution.setAssessmentKind("QUIZ");execution.setResourceType("PAPER_VERSION");execution.setResourceId(31L);execution.setResourceNameSnapshot("试卷");execution.setDurationSecondsSnapshot(60);
    execution.setPolicySnapshotJson("{\"knowledgePointSnapshots\":[{\"questionId\":5,\"questionVersionId\":9,\"knowledgePointIds\":[7]}]}");new AssessmentExecutionSnapshotCodec(mapper).encode(execution);
    execution.setPolicySnapshotJson(null);
    assertThat(CourseKnowledgeSnapshotService.read(execution,mapper)).singleElement().satisfies(q->assertThat(q.knowledgePointIds()).containsExactly(7L));
  }
  @Test void freezesPaperQuestionVersionsAndKnowledgeBeforeFirstAttempt() {
    var paper=mock(PaperVersionQuestionMapper.class);var relations=mock(QuestionKnowledgeMapper.class);
    var q=new PaperVersionQuestion();q.setQuestionId(5L);q.setQuestionVersionId(9L);when(paper.selectList(any())).thenReturn(List.of(q));
    var relation=new QuestionKnowledge();relation.setQuestionId(5L);relation.setKnowledgePointId(7L);when(relations.selectList(any())).thenReturn(List.of(relation));
    var service=new CourseKnowledgeSnapshotService(paper,mock(QuestionMapper.class),mock(QuestionVersionMapper.class),relations);
    var snapshots=service.freeze(31L,null);
    assertThat(snapshots).hasSize(1);assertThat(snapshots.get(0).questionVersionId()).isEqualTo(9L);assertThat(snapshots.get(0).knowledgePointIds()).containsExactly(7L);
  }
}

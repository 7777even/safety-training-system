package com.sts.training.service;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.port.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;
/** 计划未开始并不代表已有安排和学习事实可以覆盖。 */
class TrainingPlanChangeSafetyTest {
  TrainingPlanStageMapper stages=mock(TrainingPlanStageMapper.class);TrainingPlanContentMapper contents=mock(TrainingPlanContentMapper.class);TrainingExecutionLinkMapper links=mock(TrainingExecutionLinkMapper.class);TrainingOfflineSessionMapper sessions=mock(TrainingOfflineSessionMapper.class);TrainingParticipantMapper participants=mock(TrainingParticipantMapper.class);TrainingParticipantContentResultMapper results=mock(TrainingParticipantContentResultMapper.class);TrainingCourseExecutionPort course=mock(TrainingCourseExecutionPort.class);TrainingAssessmentExecutionPort exams=mock(TrainingAssessmentExecutionPort.class);
  TrainingPlanChangeSafety safety=new TrainingPlanChangeSafety(stages,contents,links,sessions,participants,results,course,exams);TrainingPlan plan;
  @BeforeEach void setup(){plan=new TrainingPlan();plan.setTrainingPlanId(5L);plan.setPublishStatus("PUBLISHED");}
  @Test void noExecutionsOrFactsAllowsFullChange(){safety.requireFullChangeSafe(plan);}
  @Test void generatedExecutionBlocksReplacement(){when(links.selectCount(any())).thenReturn(1L);assertThatThrownBy(()->safety.requireFullChangeSafe(plan)).hasMessageContaining("安全同步");}
  @Test void orphanDraftOfferingIsAlsoProtected(){var stage=new TrainingPlanStage();stage.setPlanStageId(1L);when(stages.selectList(any())).thenReturn(List.of(stage));var content=new TrainingPlanContent();content.setPlanContentId(2L);content.setContentType("COURSE");when(contents.selectList(any())).thenReturn(List.of(content));when(course.findOfferingIdByPlanContentId(2L)).thenReturn(3L);assertThatThrownBy(()->safety.requireFullChangeSafe(plan)).hasMessageContaining("安全同步");}
  @Test void candidateOrEnrollmentFactBlocksEvenWithoutLink(){var participant=new TrainingParticipant();participant.setTrainingParticipantId(1L);when(participants.selectList(any())).thenReturn(List.of(participant));when(exams.existsCandidateForParticipant(1L)).thenReturn(true);assertThatThrownBy(()->safety.requireFullChangeSafe(plan)).hasMessageContaining("作答");}
  @Test void resultProjectionIsNeverDeletedByChange(){var participant=new TrainingParticipant();participant.setTrainingParticipantId(1L);when(participants.selectList(any())).thenReturn(List.of(participant));when(results.selectCount(any())).thenReturn(1L);assertThatThrownBy(()->safety.requireFullChangeSafe(plan)).hasMessageContaining("结果");verify(results,never()).delete(any());}
}

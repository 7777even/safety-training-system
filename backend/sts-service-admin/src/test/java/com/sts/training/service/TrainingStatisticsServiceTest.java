package com.sts.training.service;
import com.sts.training.entity.*;
import com.sts.training.mapper.*;
import com.sts.training.vo.*;
import com.sts.admin.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
/** 汇总、完成人员筛选及零分母按同一正式结果计算。 */
class TrainingStatisticsServiceTest {
  @Test void completionSummaryAndPeopleShareFormalResultRule() {
    var plans=mock(TrainingPlanMapper.class);var participants=mock(TrainingParticipantMapper.class);var results=mock(TrainingResultSyncService.class);
    var plan=new TrainingPlan();plan.setTrainingPlanId(1L);plan.setPlanName("计划");plan.setPublishStatus("RUNNING");when(plans.selectList(any())).thenReturn(List.of(plan));
    var one=participant(11L,7L);var two=participant(12L,8L);var three=participant(13L,9L);when(participants.selectList(any())).thenReturn(List.of(one,two,three));
    when(results.getCurrentResults(1L,11L)).thenReturn(result("PASSED"));when(results.getCurrentResults(1L,12L)).thenReturn(result("FAILED"));when(results.getCurrentResults(1L,13L)).thenReturn(result("INCOMPLETE"));
    var service=new TrainingStatisticsService(mock(TrainingPlanService.class),plans,participants,mock(TrainingPlanStageMapper.class),mock(TrainingPlanContentMapper.class),mock(SysUserMapper.class),results);
    assertThat(service.statistics(null,1,20).list()).singleElement().satisfies(s->{assertThat(s.completedCount()).isEqualTo(1);assertThat(s.failedCount()).isEqualTo(1);assertThat(s.incompleteCount()).isEqualTo(1);assertThat(s.completionRate()).isEqualByComparingTo("33.33");});
    assertThat(service.people(1L,true,null,1,20).list()).singleElement().satisfies(p->assertThat(p.trainingParticipantId()).isEqualTo(11L));
    verify(results,never()).synchronize(any());verify(participants,never()).updateById(any(TrainingParticipant.class));
  }
  @Test void emptyPlanHasZeroCompletionWithoutSourceReads() {
    var plans=mock(TrainingPlanMapper.class);var plan=new TrainingPlan();plan.setTrainingPlanId(1L);when(plans.selectList(any())).thenReturn(List.of(plan));var results=mock(TrainingResultSyncService.class);
    var service=new TrainingStatisticsService(mock(TrainingPlanService.class),plans,mock(TrainingParticipantMapper.class),mock(TrainingPlanStageMapper.class),mock(TrainingPlanContentMapper.class),mock(SysUserMapper.class),results);
    assertThat(service.statistics(null,1,20).list()).singleElement().satisfies(s->{assertThat(s.expectedCount()).isZero();assertThat(s.completionRate()).isZero();});verifyNoInteractions(results);
  }
  private static TrainingParticipant participant(Long id,Long userId) {var p=new TrainingParticipant();p.setTrainingPlanId(1L);p.setTrainingParticipantId(id);p.setUserId(userId);return p;}
  private static TrainingParticipantResultsVO result(String status) {var r=new TrainingParticipantResultsVO();r.setResultStatus(status);r.setItems(List.of());return r;}
}

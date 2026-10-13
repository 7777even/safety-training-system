package com.sts.person.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.person.dto.PersonClassificationCondition;
import com.sts.person.entity.PersonGroup;
import com.sts.person.mapper.PersonClassificationMapper;
import com.sts.person.mapper.PersonGroupMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.service.TrainingResultSyncService;
import com.sts.training.vo.TrainingParticipantResultsVO;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 按当前条件展开名单；不读取任何已下发任务名单。 */
class PersonClassificationServiceTest {
  private final PersonGroupMapper groups = mock(PersonGroupMapper.class);
  private final PersonClassificationMapper people = mock(PersonClassificationMapper.class);
  private final ObjectMapper json = new ObjectMapper();
  private final TrainingParticipantMapper participants = mock(TrainingParticipantMapper.class);
  private final TrainingResultSyncService results = mock(TrainingResultSyncService.class);
  private final ObjectProvider<TrainingResultSyncService> resultProvider = mock(ObjectProvider.class);
  private final PersonClassificationService service = new PersonClassificationService(groups, people, json, mock(SysUserMapper.class), participants, resultProvider);
  @BeforeEach void setup() { TenantContext.setTenantId(1L); when(resultProvider.getObject()).thenReturn(results); }
  @AfterEach void cleanup() { TenantContext.clear(); }

  @Test void groupExpandsCurrentMembersAndRemovesDuplicates() throws Exception {
    PersonGroup group = new PersonGroup(); group.setGroupId(4L); group.setOrganizationId(1L); group.setStatus(1);
    group.setConditionsJson(json.writeValueAsString(new PersonClassificationCondition()));
    when(groups.selectOne(any())).thenReturn(group);
    when(people.resolve(anyLong(), any(), any())).thenReturn(List.of(8L, 7L, 8L));
    assertThat(service.resolveGroups(List.of(4L, 4L))).containsExactly(7L, 8L);
    verify(people, times(1)).resolve(eq(1L), any(), any());
  }

  @Test void disabledGroupCannotDispatch() {
    PersonGroup group = new PersonGroup(); group.setStatus(0);
    when(groups.selectOne(any())).thenReturn(group);
    assertThatThrownBy(() -> service.resolveGroups(List.of(4L))).isInstanceOf(BusinessException.class);
    verifyNoInteractions(people);
  }

  @Test void invalidAgeRangeIsRejectedBeforeQuery() {
    PersonClassificationCondition condition = new PersonClassificationCondition();
    condition.setMinAge(40); condition.setMaxAge(20);
    assertThatThrownBy(() -> service.resolve(condition)).isInstanceOf(BusinessException.class);
    verifyNoInteractions(people);
  }

  @Test void missingGroupCannotLeakMembers() {
    assertThatThrownBy(() -> service.resolveGroups(List.of(99L))).isInstanceOf(BusinessException.class);
    verifyNoInteractions(people);
  }

  @Test void resultFilterNeedsPlanAndRejectsUnknownStatus() {
    PersonClassificationCondition condition = new PersonClassificationCondition(); condition.setResultStatuses(List.of("PASSED"));
    assertThatThrownBy(() -> service.resolve(condition)).isInstanceOf(BusinessException.class);
    condition.setTrainingPlanId(1L); condition.setResultStatuses(List.of("FAKE"));
    assertThatThrownBy(() -> service.resolve(condition)).isInstanceOf(BusinessException.class);
    verifyNoInteractions(people);
  }

  @Test void corruptedConditionNeverFallsBackToAllUsers() {
    PersonGroup group = new PersonGroup(); group.setStatus(1); group.setConditionsJson("not-json");
    when(groups.selectOne(any())).thenReturn(group);
    assertThatThrownBy(() -> service.resolveGroups(List.of(4L))).isInstanceOf(BusinessException.class);
    verifyNoInteractions(people);
  }

  @Test void emptyGroupCannotDispatch() throws Exception {
    PersonGroup group = new PersonGroup(); group.setStatus(1);
    group.setConditionsJson(json.writeValueAsString(new PersonClassificationCondition()));
    when(groups.selectOne(any())).thenReturn(group); when(people.resolve(anyLong(),any(),any())).thenReturn(List.of());
    assertThatThrownBy(() -> service.resolveGroups(List.of(4L))).isInstanceOf(BusinessException.class);
  }

  @Test void dynamicFilterReadsCurrentResultsInsteadOfStoredSummary() {
    PersonClassificationCondition condition = new PersonClassificationCondition();
    condition.setTrainingPlanId(3L); condition.setResultStatuses(List.of("PASSED"));
    when(people.resolve(anyLong(),any(),any())).thenReturn(List.of(7L));
    TrainingParticipant person = new TrainingParticipant(); person.setTrainingPlanId(3L);person.setUserId(7L);
    person.setTrainingParticipantId(10L);person.setResultStatus("INCOMPLETE");
    when(participants.selectList(any())).thenReturn(List.of(person));
    TrainingParticipantResultsVO current = new TrainingParticipantResultsVO(); current.setResultStatus("PASSED");
    when(results.getCurrentResults(3L,10L)).thenReturn(current);
    assertThat(service.resolve(condition)).containsExactly(7L);
    current.setResultStatus("FAILED");
    assertThat(service.resolve(condition)).isEmpty();
    verify(results,never()).synchronize(anyLong());
  }
}

package com.sts.training.service;

import com.sts.common.exception.BusinessException;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.storage.StorageService;
import com.sts.training.dto.TrainingRewardPenaltyCreateRequest;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrainingRewardPenaltyServiceImplTest {

  @Test
  void openEvidence_returnsBoundFileOnly() {
    TrainingRewardPenaltyMapper mapper = mock(TrainingRewardPenaltyMapper.class);
    SysFileInfoMapper fileMapper = mock(SysFileInfoMapper.class);
    StorageService storage = mock(StorageService.class);
    TrainingRewardPenalty record = new TrainingRewardPenalty();
    record.setEvidenceFileId(30L);
    SysFileInfo file = new SysFileInfo();
    file.setFileName("证明.pdf");
    file.setObjectName("private/evidence.pdf");
    when(mapper.selectById(10L)).thenReturn(record);
    when(fileMapper.selectById(30L)).thenReturn(file);
    when(storage.download("private/evidence.pdf")).thenReturn(new ByteArrayResource(new byte[] {1, 2}));
    TrainingRewardPenaltyServiceImpl service = new TrainingRewardPenaltyServiceImpl(mapper,
      mock(TrainingResultSyncService.class), mock(TrainingPlanMapper.class),
      mock(TrainingParticipantMapper.class), fileMapper, storage);

    org.assertj.core.api.Assertions.assertThat(((ByteArrayResource) service.openEvidence(10L).resource()).getByteArray()).hasSize(2);
    verify(fileMapper).selectById(30L);
  }

  @Test
  void openEvidence_withoutBoundFile_isRejected() {
    TrainingRewardPenaltyMapper mapper = mock(TrainingRewardPenaltyMapper.class);
    StorageService storage = mock(StorageService.class);
    when(mapper.selectById(10L)).thenReturn(new TrainingRewardPenalty());
    TrainingRewardPenaltyServiceImpl service = new TrainingRewardPenaltyServiceImpl(mapper,
      mock(TrainingResultSyncService.class), mock(TrainingPlanMapper.class),
      mock(TrainingParticipantMapper.class), mock(SysFileInfoMapper.class), storage);

    assertThatThrownBy(() -> service.openEvidence(10L)).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(8247);
    verifyNoInteractions(storage);
  }

  @Test
  void create_withoutVerifiedRunningPlan_isRejectedBeforeWrite() {
    TrainingRewardPenaltyMapper mapper = mock(TrainingRewardPenaltyMapper.class);
    TrainingResultSyncService resultSync = mock(TrainingResultSyncService.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingRewardPenaltyServiceImpl service = new TrainingRewardPenaltyServiceImpl(
      mapper, resultSync, planMapper, mock(TrainingParticipantMapper.class), mock(SysFileInfoMapper.class), mock(StorageService.class));
    TrainingRewardPenaltyCreateRequest request = new TrainingRewardPenaltyCreateRequest();
    request.setTrainingPlanId(10L);
    request.setTrainingParticipantId(20L);
    request.setRecordType("REWARD");
    request.setTitle("培训表现优秀");
    request.setScoreChange(new BigDecimal("2"));
    request.setStatus(1);

    assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(8101);
    verify(planMapper).selectById(10L);
    verifyNoInteractions(mapper, resultSync);
  }

  @Test
  void create_rejectsParticipantFromAnotherPlanBeforeWrite() {
    TrainingRewardPenaltyMapper mapper = mock(TrainingRewardPenaltyMapper.class);
    TrainingResultSyncService resultSync = mock(TrainingResultSyncService.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingParticipantMapper participantMapper = mock(TrainingParticipantMapper.class);
    TrainingRewardPenaltyServiceImpl service = new TrainingRewardPenaltyServiceImpl(
      mapper, resultSync, planMapper, participantMapper, mock(SysFileInfoMapper.class), mock(StorageService.class));
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(10L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus("RUNNING");
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingPlanId(11L);
    participant.setOrganizationId(1L);
    when(planMapper.selectById(10L)).thenReturn(plan);
    when(participantMapper.selectById(20L)).thenReturn(participant);

    assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(8243);
    verifyNoInteractions(mapper, resultSync);
  }

  @Test
  void create_endedPlanDoesNotWriteOrRefresh() {
    TrainingRewardPenaltyMapper mapper = mock(TrainingRewardPenaltyMapper.class);
    TrainingResultSyncService resultSync = mock(TrainingResultSyncService.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingParticipantMapper participantMapper = mock(TrainingParticipantMapper.class);
    TrainingRewardPenaltyServiceImpl service = new TrainingRewardPenaltyServiceImpl(
      mapper, resultSync, planMapper, participantMapper, mock(SysFileInfoMapper.class), mock(StorageService.class));
    TrainingPlan plan = new TrainingPlan();
    plan.setPublishStatus("ENDED");
    when(planMapper.selectById(10L)).thenReturn(plan);

    assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(8242);
    verifyNoInteractions(mapper, participantMapper, resultSync);
  }

  @Test
  void create_planVersionConflictDoesNotWriteOrRefresh() {
    TrainingRewardPenaltyMapper mapper = mock(TrainingRewardPenaltyMapper.class);
    TrainingResultSyncService resultSync = mock(TrainingResultSyncService.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingParticipantMapper participantMapper = mock(TrainingParticipantMapper.class);
    TrainingRewardPenaltyServiceImpl service = new TrainingRewardPenaltyServiceImpl(
      mapper, resultSync, planMapper, participantMapper, mock(SysFileInfoMapper.class), mock(StorageService.class));
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(10L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus("RUNNING");
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingPlanId(10L);
    participant.setOrganizationId(1L);
    when(planMapper.selectById(10L)).thenReturn(plan);
    when(participantMapper.selectById(20L)).thenReturn(participant);
    when(planMapper.updateById(plan)).thenReturn(0);

    assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(8104);
    verifyNoInteractions(mapper, resultSync);
  }

  @Test
  void create_runningPlanWritesThenRefreshesResult() {
    TrainingRewardPenaltyMapper mapper = mock(TrainingRewardPenaltyMapper.class);
    TrainingResultSyncService resultSync = mock(TrainingResultSyncService.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingParticipantMapper participantMapper = mock(TrainingParticipantMapper.class);
    TrainingRewardPenaltyServiceImpl service = new TrainingRewardPenaltyServiceImpl(
      mapper, resultSync, planMapper, participantMapper, mock(SysFileInfoMapper.class), mock(StorageService.class));
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(10L);
    plan.setOrganizationId(1L);
    plan.setPublishStatus("RUNNING");
    TrainingParticipant participant = new TrainingParticipant();
    participant.setTrainingPlanId(10L);
    participant.setOrganizationId(1L);
    when(planMapper.selectById(10L)).thenReturn(plan);
    when(participantMapper.selectById(20L)).thenReturn(participant);
    when(planMapper.updateById(plan)).thenReturn(1);
    when(mapper.insert(org.mockito.ArgumentMatchers.any(TrainingRewardPenalty.class))).thenReturn(1);

    service.create(request());

    verify(mapper).insert(org.mockito.ArgumentMatchers.any(TrainingRewardPenalty.class));
    verify(resultSync).synchronizeParticipant(20L);
  }

  /** 为各测试提供同一份来自奖惩创建接口的有效请求。 */
  private TrainingRewardPenaltyCreateRequest request() {
    TrainingRewardPenaltyCreateRequest request = new TrainingRewardPenaltyCreateRequest();
    request.setTrainingPlanId(10L);
    request.setTrainingParticipantId(20L);
    request.setRecordType("REWARD");
    request.setTitle("培训表现优秀");
    request.setScoreChange(new BigDecimal("2"));
    request.setStatus(1);
    return request;
  }
}

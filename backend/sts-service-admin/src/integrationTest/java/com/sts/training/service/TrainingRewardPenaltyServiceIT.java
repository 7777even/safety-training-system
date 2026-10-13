package com.sts.training.service;

import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.dto.TrainingRewardPenaltyCreateRequest;
import com.sts.training.dto.TrainingRewardPenaltyUpdateRequest;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.support.TrainingRewardPenaltyItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 真实 MySQL 验证奖惩归属、可清空字段与计划结束乐观锁。 */
@SpringBootTest(classes = TrainingRewardPenaltyItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingRewardPenaltyServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired private OrganizationMapper organizationMapper;
  @Autowired private TrainingTypeMapper typeMapper;
  @Autowired private TrainingPlanMapper planMapper;
  @Autowired private TrainingParticipantMapper participantMapper;
  @Autowired private TrainingRewardPenaltyMapper rewardMapper;
  @Autowired private TrainingRewardPenaltyService rewardService;
  @Autowired private TrainingResultSyncService resultSync;
  @Autowired private SysFileInfoMapper fileMapper;
  @Autowired private StorageService storageService;

  @Test
  void evidenceReadsBoundFileButNotAnotherTenantRecord() {
    TrainingParticipant participant = seedParticipant();
    SysFileInfo file = new SysFileInfo();
    file.setFileName("奖惩证明.pdf");
    file.setBucketName("general");
    file.setObjectName("test/evidence.pdf");
    file.setFileSize(2L);
    file.setFileSuffix("pdf");
    file.setFileUrl("minio-private://test/evidence.pdf");
    fileMapper.insert(file);
    TrainingRewardPenaltyCreateRequest create = createRequest(participant);
    create.setEvidenceFileId(file.getFileInfoId());
    Long recordId = rewardService.create(create);
    TrainingRewardPenalty saved = rewardMapper.selectById(recordId);
    assertThat(saved.getDescription()).isNull();
    assertThat(saved.getOccurredDate()).isNull();
    when(storageService.download("test/evidence.pdf")).thenReturn(new ByteArrayResource(new byte[] {1, 2}));

    assertThat(rewardService.openEvidence(recordId).fileName()).isEqualTo("奖惩证明.pdf");
    assertThatThrownBy(() -> TenantContext.callWithTenant(demoTenantId() + 1_000_000,
      () -> rewardService.openEvidence(recordId)))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8241);
  }

  @Test
  void createAndClearOptionalFields_thenEndedPlanRejectsFurtherWrites() {
    TrainingParticipant participant = seedParticipant();
    TrainingRewardPenaltyCreateRequest create = createRequest(participant);
    create.setDescription("现场表扬");
    Long recordId = rewardService.create(create);
    TrainingRewardPenalty saved = rewardMapper.selectById(recordId);
    assertThat(saved.getScoreChange()).isEqualByComparingTo("2");
    verify(resultSync).synchronizeParticipant(participant.getTrainingParticipantId());

    TrainingRewardPenaltyUpdateRequest update = new TrainingRewardPenaltyUpdateRequest();
    update.setVersion(saved.getVersion());
    update.setRecordType("REWARD");
    update.setTitle("培训表现优秀");
    update.setStatus(1);
    rewardService.update(recordId, update);
    TrainingRewardPenalty cleared = rewardMapper.selectById(recordId);
    assertThat(cleared.getDescription()).isNull();
    assertThat(cleared.getScoreChange()).isNull();

    TrainingPlan plan = planMapper.selectById(participant.getTrainingPlanId());
    plan.setPublishStatus("ENDED");
    assertThat(planMapper.updateById(plan)).isEqualTo(1);
    assertThatThrownBy(() -> rewardService.delete(recordId))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8242);
    assertThat(rewardMapper.selectById(recordId)).isNotNull();
  }

  @Test
  void rewardWriteBumpsPlanVersion_soStaleEndCannotFreeze() {
    TrainingParticipant participant = seedParticipant();
    TrainingPlan staleEndPlan = planMapper.selectById(participant.getTrainingPlanId());

    rewardService.create(createRequest(participant));

    staleEndPlan.setPublishStatus("ENDED");
    assertThat(planMapper.updateById(staleEndPlan)).isZero();
    assertThat(planMapper.selectById(participant.getTrainingPlanId()).getPublishStatus()).isEqualTo("RUNNING");
  }

  /** 创建与正式培训计划相同租户的最小参训事实。 */
  private TrainingParticipant seedParticipant() {
    Long orgId = organizationMapper.selectByOrgCode("DEMO").getOrganizationId();
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode("IT-REWARD-" + System.nanoTime());
    type.setTypeName("奖惩IT");
    // 培训类型描述在库内为 NOT NULL 且无默认值，夹具必须显式赋值。
    type.setDescription("奖惩 IT 用培训类型");
    type.setStatus(1);
    typeMapper.insert(type);
    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(orgId);
    plan.setPlanCode("IT-REWARD-" + System.nanoTime());
    plan.setPlanName("奖惩IT");
    plan.setTrainingTypeId(type.getTrainingTypeId());
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now());
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("RUNNING");
    plan.setStatus(1);
    planMapper.insert(plan);
    TrainingParticipant participant = new TrainingParticipant();
    participant.setOrganizationId(orgId);
    participant.setTrainingPlanId(plan.getTrainingPlanId());
    participant.setUserId(8800L);
    participant.setParticipantOrgId(orgId);
    participant.setJoinSource("ASSIGN");
    participant.setJoinedTime(LocalDateTime.now());
    participant.setParticipantStatus("LEARNING");
    participant.setOnlineHours(BigDecimal.ZERO);
    participant.setOfflineHours(BigDecimal.ZERO);
    participantMapper.insert(participant);
    return participant;
  }

  /** 构造计划内奖励请求，关联标识来自真实插入结果。 */
  private TrainingRewardPenaltyCreateRequest createRequest(TrainingParticipant participant) {
    TrainingRewardPenaltyCreateRequest request = new TrainingRewardPenaltyCreateRequest();
    request.setTrainingPlanId(participant.getTrainingPlanId());
    request.setTrainingParticipantId(participant.getTrainingParticipantId());
    request.setRecordType("REWARD");
    request.setTitle("培训表现优秀");
    request.setScoreChange(new BigDecimal("2"));
    request.setStatus(1);
    return request;
  }
}

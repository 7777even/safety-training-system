package com.sts.training.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.common.exception.BusinessException;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.entity.TrainingAttachment;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.support.TrainingWorkflowItConfig;
import com.sts.training.vo.TrainingLearnerMaterialVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 真实 MySQL 验证学员侧只读资料的收口：只本人、只本人计划、只一般资料、只启用态。
 */
@SpringBootTest(classes = TrainingWorkflowItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingWorkflowServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired private OrganizationMapper organizationMapper;
  @Autowired private TrainingTypeMapper typeMapper;
  @Autowired private TrainingPlanMapper planMapper;
  @Autowired private TrainingPlanStageMapper stageMapper;
  @Autowired private TrainingParticipantMapper participantMapper;
  @Autowired private TrainingAttachmentMapper attachmentMapper;
  @Autowired private SysFileInfoMapper fileMapper;
  @Autowired private SysUserMapper userMapper;
  @Autowired private StorageService storageService;
  @Autowired private TrainingWorkflowService workflowService;

  @Test
  void listLearnerMaterials_returnsOnlyOwnEnabledGeneralMaterials() {
    TrainingPlan plan = seedPlan("IT-LEARNER-MAT");
    TrainingPlanStage stage = seedStage(plan);
    Long ownerId = seedUser("IT-LEARNER-MAT-A");
    Long participantId = seedParticipant(plan, ownerId);

    Long generalEnabledId = seedAttachment(plan, stage, "GENERAL", "制度文件.pdf", 1, 1);
    seedAttachment(plan, stage, "GENERAL", "停用的制度.pdf", 2, 0);
    seedAttachment(plan, stage, "IMAGE", "现场照片.png", 3, 1);
    seedAttachment(plan, stage, "VIDEO", "现场录像.mp4", 4, 1);
    seedAttachment(plan, stage, "EVALUATION", "评价佐证.pdf", 5, 1);

    List<TrainingLearnerMaterialVO> materials =
      workflowService.listLearnerMaterials(participantId, ownerId);

    // 只返回启用的一般资料：停用资料与影像/评价佐证都不下发。
    assertThat(materials).hasSize(1);
    TrainingLearnerMaterialVO only = materials.get(0);
    assertThat(only.getTrainingAttachmentId()).isEqualTo(generalEnabledId);
    assertThat(only.getAttachmentName()).isEqualTo("制度文件.pdf");
    assertThat(only.getStageName()).isEqualTo("线下阶段");
    assertThat(only.getPlanStageId()).isEqualTo(stage.getPlanStageId());
    assertThat(only.getHasFile()).isTrue();
  }

  @Test
  void listLearnerMaterials_rejectsOtherAndUnknownParticipantsWithSameResult() {
    TrainingPlan plan = seedPlan("IT-LEARNER-FOREIGN");
    seedStage(plan);
    Long ownerId = seedUser("IT-LEARNER-FOREIGN-A");
    Long strangerId = seedUser("IT-LEARNER-FOREIGN-B");
    Long participantId = seedParticipant(plan, ownerId);

    // 他人参训记录与不存在的参训记录返回同一业务码，避免借错误码区分记录是否存在。
    assertThatThrownBy(() -> workflowService.listLearnerMaterials(participantId, strangerId))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155);
    assertThatThrownBy(() -> workflowService.listLearnerMaterials(participantId + 100_000L, ownerId))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155);
  }

  @Test
  void openLearnerMaterialFile_onlyServesOwnEnabledGeneralMaterial() {
    TrainingPlan plan = seedPlan("IT-LEARNER-FILE");
    TrainingPlan otherPlan = seedPlan("IT-LEARNER-FILE-OTHER");
    TrainingPlanStage stage = seedStage(plan);
    TrainingPlanStage otherStage = seedStage(otherPlan);
    Long ownerId = seedUser("IT-LEARNER-FILE-A");
    Long strangerId = seedUser("IT-LEARNER-FILE-B");
    Long participantId = seedParticipant(plan, ownerId);

    Long generalEnabledId = seedAttachment(plan, stage, "GENERAL", "制度文件.pdf", 1, 1);
    Long generalDisabledId = seedAttachment(plan, stage, "GENERAL", "停用的制度.pdf", 2, 0);
    Long imageId = seedAttachment(plan, stage, "IMAGE", "现场照片.png", 3, 1);
    Long otherPlanId = seedAttachment(otherPlan, otherStage, "GENERAL", "别的计划.pdf", 1, 1);

    when(storageService.download("test/制度文件.pdf"))
      .thenReturn(new ByteArrayResource(new byte[] {1, 2, 3}));

    TrainingAttachmentFile opened =
      workflowService.openLearnerMaterialFile(participantId, generalEnabledId, ownerId);
    assertThat(opened.fileName()).isEqualTo("制度文件.pdf");
    assertThat(opened.fileSize()).isEqualTo(3L);

    // 非一般资料、停用资料、跨计划资料一律按不可读处理，不泄漏存在性。
    assertThatThrownBy(() -> workflowService.openLearnerMaterialFile(participantId, imageId, ownerId))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8233);
    assertThatThrownBy(() -> workflowService.openLearnerMaterialFile(participantId, generalDisabledId, ownerId))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8233);
    assertThatThrownBy(() -> workflowService.openLearnerMaterialFile(participantId, otherPlanId, ownerId))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8233);
    // 主体不属于本人时先被可见性收口拦下，不进入资料判定。
    assertThatThrownBy(() -> workflowService.openLearnerMaterialFile(participantId, generalEnabledId, strangerId))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 8155);
  }

  private TrainingPlan seedPlan(String codePrefix) {
    Long orgId = organizationMapper.selectByOrgCode(DEMO_ORG_CODE).getOrganizationId();
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode(codePrefix + "-TYPE-" + System.nanoTime());
    type.setTypeName("学员资料IT");
    type.setDescription("学员侧资料 IT 用培训类型");
    type.setStatus(1);
    typeMapper.insert(type);
    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(orgId);
    plan.setPlanCode(codePrefix + "-" + System.nanoTime());
    plan.setPlanName("学员资料IT");
    plan.setTrainingTypeId(type.getTrainingTypeId());
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now().minusHours(1));
    plan.setEndTime(LocalDateTime.now().plusDays(1));
    plan.setPublishStatus("RUNNING");
    plan.setStatus(1);
    planMapper.insert(plan);
    return plan;
  }

  private TrainingPlanStage seedStage(TrainingPlan plan) {
    TrainingPlanStage stage = new TrainingPlanStage();
    stage.setTrainingPlanId(plan.getTrainingPlanId());
    stage.setStageName("线下阶段");
    stage.setStageType("OFFLINE");
    stage.setRequiredHours(BigDecimal.ZERO);
    stage.setSortOrder(1);
    stage.setStageStatus("PENDING");
    stageMapper.insert(stage);
    return stage;
  }

  private Long seedUser(String userCode) {
    SysUser user = new SysUser();
    user.setOrganizationId(demoTenantId());
    user.setUserCode(userCode + "-" + System.nanoTime());
    user.setUserName("学员资料IT-" + userCode);
    user.setStatus(1);
    userMapper.insert(user);
    return user.getUserId();
  }

  private Long seedParticipant(TrainingPlan plan, Long userId) {
    Long orgId = demoTenantId();
    TrainingParticipant participant = new TrainingParticipant();
    participant.setOrganizationId(orgId);
    participant.setTrainingPlanId(plan.getTrainingPlanId());
    participant.setUserId(userId);
    participant.setParticipantOrgId(orgId);
    participant.setJoinSource("ASSIGN");
    participant.setJoinedTime(LocalDateTime.now().minusHours(1));
    participant.setParticipantStatus("JOINED");
    participant.setOnlineHours(BigDecimal.ZERO);
    participant.setOfflineHours(BigDecimal.ZERO);
    participant.setResultStatus("INCOMPLETE");
    participantMapper.insert(participant);
    return participant.getTrainingParticipantId();
  }

  private Long seedAttachment(TrainingPlan plan, TrainingPlanStage stage, String type, String fileName,
                              int sortOrder, int status) {
    SysFileInfo file = new SysFileInfo();
    file.setFileName(fileName);
    file.setBucketName("general");
    file.setObjectName("test/" + fileName);
    file.setFileSize(3L);
    file.setFileSuffix(fileName.substring(fileName.lastIndexOf('.') + 1));
    file.setFileUrl("minio-private://test/" + fileName);
    fileMapper.insert(file);
    TrainingAttachment attachment = new TrainingAttachment();
    attachment.setTrainingPlanId(plan.getTrainingPlanId());
    attachment.setPlanStageId(stage.getPlanStageId());
    attachment.setAttachmentType(type);
    attachment.setFileInfoId(file.getFileInfoId());
    attachment.setAttachmentName(fileName);
    attachment.setSortOrder(sortOrder);
    attachment.setStatus(status);
    attachmentMapper.insert(attachment);
    return attachment.getTrainingAttachmentId();
  }
}

package com.sts.training.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.exception.BusinessException;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.training.dto.TrainingAttachmentCreateRequest;
import com.sts.training.entity.TrainingAttachment;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.support.TrainingAttachmentItConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** 真实 MySQL 验证培训资料的归属、类型、阶段与计划结束后的只读。 */
@SpringBootTest(classes = TrainingAttachmentItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TrainingAttachmentServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired private OrganizationMapper organizationMapper;
  @Autowired private TrainingTypeMapper typeMapper;
  @Autowired private TrainingPlanMapper planMapper;
  @Autowired private TrainingPlanStageMapper stageMapper;
  @Autowired private TrainingAttachmentMapper attachmentMapper;
  @Autowired private TrainingAttachmentService attachmentService;
  @Autowired private SysFileInfoMapper fileMapper;
  @Autowired private StorageService storageService;

  @Test
  void fileReadsBoundMaterialButNotAnotherTenantRecord() {
    TrainingPlan plan = seedPlan("IT-MATERIAL-READ");
    SysFileInfo file = seedFile("现场照片.png", "test/material.png");
    Long attachmentId = attachmentService.create(createRequest(plan, file, "IMAGE"));
    when(storageService.download("test/material.png")).thenReturn(new ByteArrayResource(new byte[] {1, 2}));

    assertThat(attachmentService.openFile(attachmentId).fileName()).isEqualTo("现场照片.png");
    assertThatThrownBy(() -> TenantContext.callWithTenant(demoTenantId() + 1_000_000,
      () -> attachmentService.openFile(attachmentId)))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8231);
  }

  @Test
  void createRejectsStageFromAnotherPlan() {
    TrainingPlan plan = seedPlan("IT-MATERIAL-STAGE-A");
    TrainingPlan otherPlan = seedPlan("IT-MATERIAL-STAGE-B");
    TrainingPlanStage foreignStage = seedStage(otherPlan);
    TrainingAttachmentCreateRequest request = createRequest(plan, seedFile("制度.pdf", "test/rule.pdf"), "GENERAL");
    request.setPlanStageId(foreignStage.getPlanStageId());

    assertThatThrownBy(() -> attachmentService.create(request))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8235);
    assertThat(attachmentMapper.selectList(null)).noneMatch(row -> row.getTrainingPlanId() != null
      && row.getTrainingPlanId().equals(plan.getTrainingPlanId()));
  }

  @Test
  void createNormalizesTypeAndRejectsUnknownType() {
    TrainingPlan plan = seedPlan("IT-MATERIAL-TYPE");
    SysFileInfo file = seedFile("现场视频.mp4", "test/material.mp4");
    Long attachmentId = attachmentService.create(createRequest(plan, file, "video"));

    assertThat(attachmentMapper.selectById(attachmentId).getAttachmentType()).isEqualTo("VIDEO");

    TrainingAttachmentCreateRequest unknown = createRequest(plan, seedFile("说明.doc", "test/doc.doc"), "DOCUMENT");
    assertThatThrownBy(() -> attachmentService.create(unknown))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8234);

    // 「奖惩佐证」已收口到奖惩记录，作为资料类型提交同样被拒绝。
    TrainingAttachmentCreateRequest retired =
      createRequest(plan, seedFile("奖惩佐证.pdf", "test/reward.pdf"), "REWARD_PENALTY");
    assertThatThrownBy(() -> attachmentService.create(retired))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8234);
  }

  @Test
  void materialWriteDoesNotBumpPlanVersionButEndedPlanIsReadOnly() {
    TrainingPlan plan = seedPlan("IT-MATERIAL-FREEZE");
    Integer planVersionBefore = planMapper.selectById(plan.getTrainingPlanId()).getVersion();
    Long attachmentId = attachmentService.create(
      createRequest(plan, seedFile("现场照片.png", "test/material.png"), "IMAGE"));

    // 资料不参与综合结果，写入不推进计划乐观锁，避免多文件上传互相冲突。
    assertThat(planMapper.selectById(plan.getTrainingPlanId()).getVersion()).isEqualTo(planVersionBefore);

    TrainingPlan ending = planMapper.selectById(plan.getTrainingPlanId());
    ending.setPublishStatus("ENDED");
    assertThat(planMapper.updateById(ending)).isEqualTo(1);

    assertThatThrownBy(() -> attachmentService.delete(attachmentId))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8237);
    assertThat(attachmentMapper.selectById(attachmentId)).isNotNull();
  }

  @Test
  void sameFileInSamePlanIsRejectedAsDuplicate() {
    TrainingPlan plan = seedPlan("IT-MATERIAL-DUP");
    SysFileInfo file = seedFile("现场照片.png", "test/material.png");
    attachmentService.create(createRequest(plan, file, "IMAGE"));

    assertThatThrownBy(() -> attachmentService.create(createRequest(plan, file, "IMAGE")))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8232);
  }

  /** 创建与示例企业租户一致的最小培训计划。 */
  private TrainingPlan seedPlan(String planCodePrefix) {
    Long orgId = organizationMapper.selectByOrgCode(DEMO_ORG_CODE).getOrganizationId();
    TrainingType type = new TrainingType();
    type.setOrganizationId(orgId);
    type.setTypeCode("IT-MATERIAL-" + System.nanoTime());
    type.setTypeName("培训资料IT");
    type.setDescription("培训资料 IT 用培训类型");
    type.setStatus(1);
    typeMapper.insert(type);
    TrainingPlan plan = new TrainingPlan();
    plan.setOrganizationId(orgId);
    plan.setPlanCode(planCodePrefix + "-" + System.nanoTime());
    plan.setPlanName("培训资料IT");
    plan.setTrainingTypeId(type.getTrainingTypeId());
    plan.setTrainingNature("INITIAL");
    plan.setStartTime(LocalDateTime.now());
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

  private SysFileInfo seedFile(String fileName, String objectName) {
    SysFileInfo file = new SysFileInfo();
    file.setFileName(fileName);
    file.setBucketName("general");
    file.setObjectName(objectName);
    file.setFileSize(2L);
    file.setFileSuffix(fileName.substring(fileName.lastIndexOf('.') + 1));
    file.setFileUrl("minio-private://" + objectName);
    fileMapper.insert(file);
    return file;
  }

  private TrainingAttachmentCreateRequest createRequest(TrainingPlan plan, SysFileInfo file, String attachmentType) {
    TrainingAttachmentCreateRequest request = new TrainingAttachmentCreateRequest();
    request.setTrainingPlanId(plan.getTrainingPlanId());
    request.setAttachmentType(attachmentType);
    request.setFileInfoId(file.getFileInfoId());
    request.setAttachmentName(" 现场照片 ");
    request.setSortOrder(1);
    request.setStatus(1);
    return request;
  }
}

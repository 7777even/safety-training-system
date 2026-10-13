package com.sts.training.service;

import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.common.exception.BusinessException;
import com.sts.training.dto.TrainingAttachmentCreateRequest;
import com.sts.training.dto.TrainingAttachmentUpdateRequest;
import com.sts.training.entity.TrainingAttachment;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 培训资料的写入校验与受控文件读取。 */
class TrainingAttachmentServiceImplTest {

  @Test
  void openFile_returnsFileBoundToRecord() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    SysFileInfoMapper fileMapper = mock(SysFileInfoMapper.class);
    StorageService storage = mock(StorageService.class);
    TrainingAttachment attachment = new TrainingAttachment();
    attachment.setFileInfoId(30L);
    SysFileInfo file = new SysFileInfo();
    file.setFileName("现场照片.png");
    file.setObjectName("private/material.png");
    file.setFileSize(2L);
    when(mapper.selectById(10L)).thenReturn(attachment);
    when(fileMapper.selectById(30L)).thenReturn(file);
    when(storage.download("private/material.png")).thenReturn(new ByteArrayResource(new byte[] {1, 2}));
    TrainingAttachmentServiceImpl service = service(mapper, fileMapper, storage);

    TrainingAttachmentFile opened = service.openFile(10L);

    assertThat(((ByteArrayResource) opened.resource()).getByteArray()).hasSize(2);
    assertThat(opened.fileName()).isEqualTo("现场照片.png");
    assertThat(opened.fileSize()).isEqualTo(2L);
    verify(fileMapper).selectById(30L);
  }

  @Test
  void openFile_withoutBoundFile_isRejected() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    StorageService storage = mock(StorageService.class);
    when(mapper.selectById(10L)).thenReturn(new TrainingAttachment());
    TrainingAttachmentServiceImpl service = service(mapper, mock(SysFileInfoMapper.class), storage);

    assertThatThrownBy(() -> service.openFile(10L)).isInstanceOf(BusinessException.class)
      .extracting("code").isEqualTo(8233);
    verifyNoInteractions(storage);
  }

  @Test
  void create_withoutRunningPlan_isRejectedBeforeWrite() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    when(planMapper.selectById(10L)).thenReturn(null);
    TrainingAttachmentServiceImpl service = service(mapper, mock(SysFileInfoMapper.class), mock(StorageService.class), planMapper);

    assertThatThrownBy(() -> service.create(createRequest("IMAGE")))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8101);
    verify(planMapper).selectById(10L);
    verifyNoInteractions(mapper);
  }

  @Test
  void create_rejectsEndedPlan() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlan plan = plan(10L, "ENDED");
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    when(planMapper.selectById(10L)).thenReturn(plan);
    TrainingAttachmentServiceImpl service = service(mapper, mock(SysFileInfoMapper.class), mock(StorageService.class), planMapper);

    assertThatThrownBy(() -> service.create(createRequest("IMAGE")))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8237);
    verifyNoInteractions(mapper);
  }

  @Test
  void create_rejectsUnknownMaterialType() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    when(planMapper.selectById(10L)).thenReturn(plan(10L, "RUNNING"));
    TrainingAttachmentServiceImpl service = service(mapper, mock(SysFileInfoMapper.class), mock(StorageService.class), planMapper);

    assertThatThrownBy(() -> service.create(createRequest("DOCUMENT")))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8234);
    verifyNoInteractions(mapper);
  }

  /** 奖惩佐证已收口到奖惩记录，资料类型不再接受 REWARD_PENALTY。 */
  @Test
  void create_rejectsRetiredRewardPenaltyMaterialType() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    when(planMapper.selectById(10L)).thenReturn(plan(10L, "RUNNING"));
    TrainingAttachmentServiceImpl service = service(mapper, mock(SysFileInfoMapper.class), mock(StorageService.class), planMapper);

    assertThatThrownBy(() -> service.create(createRequest("REWARD_PENALTY")))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8234);
    verifyNoInteractions(mapper);
  }

  /** 编辑同样不得把资料改成已收口的奖惩佐证类型。 */
  @Test
  void update_rejectsRetiredRewardPenaltyMaterialType() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingAttachment existing = new TrainingAttachment();
    existing.setTrainingPlanId(10L);
    existing.setVersion(0);
    when(mapper.selectById(7L)).thenReturn(existing);
    when(planMapper.selectById(10L)).thenReturn(plan(10L, "RUNNING"));
    TrainingAttachmentServiceImpl service = service(mapper, mock(SysFileInfoMapper.class), mock(StorageService.class), planMapper);
    TrainingAttachmentUpdateRequest request = new TrainingAttachmentUpdateRequest();
    request.setAttachmentType("REWARD_PENALTY");
    request.setFileInfoId(30L);
    request.setAttachmentName("奖惩佐证");
    request.setSortOrder(1);
    request.setStatus(1);
    request.setVersion(0);

    assertThatThrownBy(() -> service.update(7L, request))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8234);
  }

  @Test
  void create_rejectsStageFromAnotherPlan() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    TrainingPlanStageMapper stageMapper = mock(TrainingPlanStageMapper.class);
    when(planMapper.selectById(10L)).thenReturn(plan(10L, "RUNNING"));
    TrainingPlanStage foreign = new TrainingPlanStage();
    foreign.setPlanStageId(5L);
    foreign.setTrainingPlanId(99L);
    when(stageMapper.selectById(5L)).thenReturn(foreign);
    TrainingAttachmentServiceImpl service = service(
      mapper, mock(SysFileInfoMapper.class), mock(StorageService.class), planMapper, stageMapper);
    TrainingAttachmentCreateRequest request = createRequest("VIDEO");
    request.setPlanStageId(5L);

    assertThatThrownBy(() -> service.create(request))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8235);
    verifyNoInteractions(mapper);
  }

  @Test
  void create_rejectsInvisibleFile() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    SysFileInfoMapper fileMapper = mock(SysFileInfoMapper.class);
    when(planMapper.selectById(10L)).thenReturn(plan(10L, "RUNNING"));
    when(fileMapper.selectById(30L)).thenReturn(null);
    TrainingAttachmentServiceImpl service = service(mapper, fileMapper, mock(StorageService.class), planMapper);

    assertThatThrownBy(() -> service.create(createRequest("IMAGE")))
      .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(8236);
    verifyNoInteractions(mapper);
  }

  @Test
  void create_normalizesAndPersistsMaterial() {
    TrainingAttachmentMapper mapper = mock(TrainingAttachmentMapper.class);
    TrainingPlanMapper planMapper = mock(TrainingPlanMapper.class);
    SysFileInfoMapper fileMapper = mock(SysFileInfoMapper.class);
    SysFileInfo file = new SysFileInfo();
    file.setObjectName("private/material.png");
    when(planMapper.selectById(10L)).thenReturn(plan(10L, "RUNNING"));
    when(fileMapper.selectById(30L)).thenReturn(file);
    TrainingAttachmentServiceImpl service = service(mapper, fileMapper, mock(StorageService.class), planMapper);

    service.create(createRequest("image"));

    verify(mapper).insert(org.mockito.ArgumentMatchers.<TrainingAttachment>argThat(saved ->
      "IMAGE".equals(saved.getAttachmentType()) && "现场照片".equals(saved.getAttachmentName())));
  }

  private static TrainingAttachmentServiceImpl service(TrainingAttachmentMapper mapper, SysFileInfoMapper fileMapper,
                                                       StorageService storage) {
    return service(mapper, fileMapper, storage, mock(TrainingPlanMapper.class));
  }

  private static TrainingAttachmentServiceImpl service(TrainingAttachmentMapper mapper, SysFileInfoMapper fileMapper,
                                                       StorageService storage, TrainingPlanMapper planMapper) {
    return service(mapper, fileMapper, storage, planMapper, mock(TrainingPlanStageMapper.class));
  }

  private static TrainingAttachmentServiceImpl service(TrainingAttachmentMapper mapper, SysFileInfoMapper fileMapper,
                                                       StorageService storage, TrainingPlanMapper planMapper,
                                                       TrainingPlanStageMapper stageMapper) {
    return new TrainingAttachmentServiceImpl(mapper, planMapper, stageMapper, fileMapper, storage);
  }

  private static TrainingPlan plan(Long planId, String publishStatus) {
    TrainingPlan plan = new TrainingPlan();
    plan.setTrainingPlanId(planId);
    plan.setPublishStatus(publishStatus);
    return plan;
  }

  private static TrainingAttachmentCreateRequest createRequest(String attachmentType) {
    TrainingAttachmentCreateRequest request = new TrainingAttachmentCreateRequest();
    request.setTrainingPlanId(10L);
    request.setAttachmentType(attachmentType);
    request.setFileInfoId(30L);
    request.setAttachmentName(" 现场照片 ");
    request.setSortOrder(1);
    request.setStatus(1);
    return request;
  }
}

package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.training.dto.*;
import com.sts.training.entity.TrainingAttachment;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingPlanStage;
import com.sts.training.mapper.TrainingAttachmentMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingPlanStageMapper;
import com.sts.training.vo.TrainingAttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/**
 * 培训资料维护与受控读取。
 * <p>资料挂培训计划（{@code trainingPlanId} 必填），可选关联该计划下的培训阶段；
 * 写入前校验计划仍在进行中、阶段归属与文件可见性，避免资料挂到别的计划或不可读文件上。
 */
@Service
@RequiredArgsConstructor
public class TrainingAttachmentServiceImpl
  extends BaseServiceImpl<TrainingAttachmentMapper, TrainingAttachment>
  implements TrainingAttachmentService {

  /**
   * 资料类型约定集合；与 {@code sts_training_attachment.attachment_type} 的列注释一致。
   *
   * <p>「奖惩佐证」（{@code REWARD_PENALTY}）已收口到奖惩记录自身的佐证文件，不再作为资料类型；
   * 提交该类型返回 8234，历史数据由 {@code V20260924180000} 改写为 {@code GENERAL}。
   */
  private static final Set<String> MATERIAL_TYPES =
    Set.of("GENERAL", "IMAGE", "VIDEO", "EVALUATION");

  private final TrainingAttachmentMapper trainingAttachmentMapper;
  private final TrainingPlanMapper planMapper;
  private final TrainingPlanStageMapper planStageMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final StorageService storageService;

  @Override
  public PageResult<TrainingAttachmentVO> page(PageRequest<TrainingAttachmentQuery> request) {
    TrainingAttachmentQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingAttachment> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getTrainingPlanId() != null, TrainingAttachment::getTrainingPlanId, query.getTrainingPlanId())
        .eq(query.getPlanStageId() != null, TrainingAttachment::getPlanStageId, query.getPlanStageId())
        .eq(query.getAttachmentType() != null, TrainingAttachment::getAttachmentType, query.getAttachmentType())
        .eq(query.getStatus() != null, TrainingAttachment::getStatus, query.getStatus());
    }
    wrapper.orderByAsc(TrainingAttachment::getSortOrder);
    Page<TrainingAttachment> page = trainingAttachmentMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingAttachmentVO get(Long trainingAttachmentId) {
    return toVO(requireAttachment(trainingAttachmentId));
  }

  /** 只按当前机构可见的资料记录读取已关联文件，不接受调用方传入的文件 ID。 */
  @Override
  public TrainingAttachmentFile openFile(Long trainingAttachmentId) {
    Long fileId = requireAttachment(trainingAttachmentId).getFileInfoId();
    if (fileId == null) {
      throw fileNotFound();
    }
    SysFileInfo file = fileInfoMapper.selectById(fileId);
    if (file == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
      throw fileNotFound();
    }
    try {
      Resource resource = storageService.download(file.getObjectName());
      if (resource == null || !resource.exists()) {
        throw fileNotFound();
      }
      return new TrainingAttachmentFile(resource, file.getFileName(),
                                        file.getFileSize() == null ? 0 : file.getFileSize());
    } catch (BusinessException error) {
      throw error;
    } catch (RuntimeException error) {
      throw fileNotFound();
    }
  }

  @Override
  @Transactional
  public Long create(TrainingAttachmentCreateRequest request) {
    TrainingPlan plan = requireRunningPlan(request.getTrainingPlanId());
    String attachmentType = requireMaterialType(request.getAttachmentType());
    requireStage(plan, request.getPlanStageId());
    requireVisibleFile(request.getFileInfoId());
    TrainingAttachment attachment = new TrainingAttachment();
    attachment.setTrainingPlanId(request.getTrainingPlanId());
    attachment.setPlanStageId(request.getPlanStageId());
    attachment.setAttachmentType(attachmentType);
    attachment.setFileInfoId(request.getFileInfoId());
    attachment.setAttachmentName(request.getAttachmentName().trim());
    attachment.setDescription(request.getDescription());
    attachment.setSortOrder(request.getSortOrder());
    attachment.setStatus(request.getStatus());
    try {
      trainingAttachmentMapper.insert(attachment);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8232, "同计划下文件已存在");
    }
    return attachment.getTrainingAttachmentId();
  }

  @Override
  @Transactional
  public void update(Long trainingAttachmentId, TrainingAttachmentUpdateRequest request) {
    TrainingAttachment attachment = requireAttachment(trainingAttachmentId);
    TrainingPlan plan = requireRunningPlan(attachment.getTrainingPlanId());
    String attachmentType = requireMaterialType(request.getAttachmentType());
    requireStage(plan, request.getPlanStageId());
    requireVisibleFile(request.getFileInfoId());
    if (!Objects.equals(attachment.getVersion(), request.getVersion())) {
      throw versionConflict();
    }
    attachment.setVersion(request.getVersion());
    attachment.setPlanStageId(request.getPlanStageId());
    attachment.setAttachmentType(attachmentType);
    attachment.setFileInfoId(request.getFileInfoId());
    attachment.setAttachmentName(request.getAttachmentName().trim());
    attachment.setDescription(request.getDescription());
    attachment.setSortOrder(request.getSortOrder());
    attachment.setStatus(request.getStatus());
    if (trainingAttachmentMapper.updateById(attachment) == 0) {
      throw versionConflict();
    }
  }

  @Override
  @Transactional
  public void delete(Long trainingAttachmentId) {
    TrainingAttachment attachment = requireAttachment(trainingAttachmentId);
    requireRunningPlan(attachment.getTrainingPlanId());
    if (!this.removeById(attachment.getTrainingAttachmentId())) {
      throw versionConflict();
    }
  }

  TrainingAttachment requireAttachment(Long id) {
    TrainingAttachment attachment = trainingAttachmentMapper.selectById(id);
    if (attachment == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8231, "培训附件不存在");
    }
    return attachment;
  }

  /** 资料只在计划进行中可维护；计划结束后资料只读。 */
  private TrainingPlan requireRunningPlan(Long planId) {
    TrainingPlan plan = planMapper.selectById(planId);
    if (plan == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    }
    if (!"RUNNING".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8237, "仅进行中的培训计划可维护培训资料");
    }
    return plan;
  }

  /** 类型收敛为约定集合，避免出现无约束的自由字符串类型。 */
  private String requireMaterialType(String attachmentType) {
    String normalized = attachmentType == null ? null : attachmentType.trim().toUpperCase();
    if (normalized == null || !MATERIAL_TYPES.contains(normalized)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8234, "培训资料类型无效");
    }
    return normalized;
  }

  /** 关联阶段必须属于同一计划，防止资料挂到别的计划阶段。 */
  private void requireStage(TrainingPlan plan, Long planStageId) {
    if (planStageId == null) {
      return;
    }
    TrainingPlanStage stage = planStageMapper.selectById(planStageId);
    if (stage == null || !Objects.equals(plan.getTrainingPlanId(), stage.getTrainingPlanId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8235, "培训资料关联阶段不属于该培训计划");
    }
  }

  /** 只接受当前机构可见且已有对象键的已上传文件。 */
  private void requireVisibleFile(Long fileInfoId) {
    SysFileInfo file = fileInfoId == null ? null : fileInfoMapper.selectById(fileInfoId);
    if (file == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8236, "培训资料文件不存在或不可见");
    }
  }

  private BusinessException fileNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, 8233, "培训资料文件不存在或不可读取");
  }

  private BusinessException versionConflict() {
    return new BusinessException(HttpStatus.CONFLICT, 8238, "培训资料版本冲突");
  }

  private TrainingAttachmentVO toVO(TrainingAttachment attachment) {
    TrainingAttachmentVO vo = new TrainingAttachmentVO();
    BeanUtils.copyProperties(attachment, vo);
    return vo;
  }
}

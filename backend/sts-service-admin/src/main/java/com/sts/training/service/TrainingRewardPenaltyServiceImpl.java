package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.admin.entity.SysFileInfo;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.training.dto.*;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.entity.TrainingRewardPenalty;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import com.sts.training.mapper.TrainingRewardPenaltyMapper;
import com.sts.training.vo.TrainingRewardPenaltyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrainingRewardPenaltyServiceImpl
  extends BaseServiceImpl<TrainingRewardPenaltyMapper, TrainingRewardPenalty>
  implements TrainingRewardPenaltyService {

  private final TrainingRewardPenaltyMapper trainingRewardPenaltyMapper;
  private final TrainingResultSyncService resultSyncService;
  private final TrainingPlanMapper planMapper;
  private final TrainingParticipantMapper participantMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final StorageService storageService;

  @Override
  public PageResult<TrainingRewardPenaltyVO> page(PageRequest<TrainingRewardPenaltyQuery> request) {
    TrainingRewardPenaltyQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingRewardPenalty> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(query.getTrainingPlanId() != null, TrainingRewardPenalty::getTrainingPlanId, query.getTrainingPlanId())
        .eq(query.getTrainingParticipantId() != null, TrainingRewardPenalty::getTrainingParticipantId, query.getTrainingParticipantId())
        .eq(query.getRecordType() != null, TrainingRewardPenalty::getRecordType, query.getRecordType())
        .eq(query.getStatus() != null, TrainingRewardPenalty::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(TrainingRewardPenalty::getCreateTime);
    Page<TrainingRewardPenalty> page = trainingRewardPenaltyMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingRewardPenaltyVO get(Long rewardPenaltyId) {
    return toVO(requireRecord(rewardPenaltyId));
  }

  /** 只按当前机构可见的奖惩记录读取已关联的佐证，不暴露任意文件 ID。 */
  @Override
  public TrainingRewardEvidenceFile openEvidence(Long rewardPenaltyId) {
    Long fileId = requireRecord(rewardPenaltyId).getEvidenceFileId();
    if (fileId == null) throw evidenceNotFound();
    SysFileInfo file = fileInfoMapper.selectById(fileId);
    if (file == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
      throw evidenceNotFound();
    }
    try {
      Resource resource = storageService.download(file.getObjectName());
      if (resource == null || !resource.exists()) throw evidenceNotFound();
      return new TrainingRewardEvidenceFile(resource, file.getFileName(), file.getFileSize() == null ? 0 : file.getFileSize());
    } catch (RuntimeException error) {
      throw evidenceNotFound();
    }
  }

  private BusinessException evidenceNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, 8247, "奖惩佐证文件不存在或不可读取");
  }

  @Override
  @Transactional
  public Long create(TrainingRewardPenaltyCreateRequest request) {
    validateFields(request.getRecordType(), request.getScoreChange(), request.getStatus(), request.getEvidenceFileId());
    TrainingPlan plan = requireRunningPlan(request.getTrainingPlanId());
    requireParticipant(plan, request.getTrainingParticipantId());
    touchPlan(plan);
    TrainingRewardPenalty record = new TrainingRewardPenalty();
    record.setTrainingPlanId(request.getTrainingPlanId());
    record.setTrainingParticipantId(request.getTrainingParticipantId());
    record.setRecordType(request.getRecordType());
    record.setTitle(request.getTitle().trim());
    record.setDescription(request.getDescription());
    record.setScoreChange(request.getScoreChange());
    record.setOccurredDate(request.getOccurredDate());
    record.setEvidenceFileId(request.getEvidenceFileId());
    record.setStatus(request.getStatus());
    trainingRewardPenaltyMapper.insert(record);
    refreshComposite(record.getTrainingParticipantId());
    return record.getRewardPenaltyId();
  }

  @Override
  @Transactional
  public void update(Long rewardPenaltyId, TrainingRewardPenaltyUpdateRequest request) {
    TrainingRewardPenalty record = requireRecord(rewardPenaltyId);
    TrainingPlan plan = requireRunningPlan(record.getTrainingPlanId());
    requireParticipant(plan, record.getTrainingParticipantId());
    validateFields(request.getRecordType(), request.getScoreChange(), request.getStatus(), request.getEvidenceFileId());
    if (!Objects.equals(record.getVersion(), request.getVersion())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8244, "奖惩记录版本冲突");
    }
    touchPlan(plan);
    record.setVersion(request.getVersion());
    record.setRecordType(request.getRecordType());
    record.setTitle(request.getTitle().trim());
    record.setDescription(request.getDescription());
    record.setScoreChange(request.getScoreChange());
    record.setOccurredDate(request.getOccurredDate());
    record.setEvidenceFileId(request.getEvidenceFileId());
    record.setStatus(request.getStatus());
    if (trainingRewardPenaltyMapper.updateById(record) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8244, "奖惩记录版本冲突");
    }
    refreshComposite(record.getTrainingParticipantId());
  }

  @Override
  @Transactional
  public void delete(Long rewardPenaltyId) {
    TrainingRewardPenalty record = requireRecord(rewardPenaltyId);
    TrainingPlan plan = requireRunningPlan(record.getTrainingPlanId());
    requireParticipant(plan, record.getTrainingParticipantId());
    touchPlan(plan);
    Long participantId = record.getTrainingParticipantId();
    if (!this.removeById(record.getRewardPenaltyId())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8244, "奖惩记录版本冲突");
    }
    refreshComposite(participantId);
  }

  /** 校验奖惩归属的计划仍可写入，结束后的结果快照不得再改变。 */
  private TrainingPlan requireRunningPlan(Long planId) {
    TrainingPlan plan = planMapper.selectById(planId);
    if (plan == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8101, "培训计划不存在");
    }
    if (!"RUNNING".equals(plan.getPublishStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 8242, "仅进行中的培训计划可录入奖惩");
    }
    return plan;
  }

  /** 只允许把奖惩记在当前计划的真实参训人名下。 */
  private void requireParticipant(TrainingPlan plan, Long participantId) {
    TrainingParticipant participant = participantMapper.selectById(participantId);
    if (participant == null || !Objects.equals(plan.getTrainingPlanId(), participant.getTrainingPlanId())
        || !Objects.equals(plan.getOrganizationId(), participant.getOrganizationId())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8243, "参训人不属于当前培训计划");
    }
  }

  /** 与结束培训共用计划乐观锁，防止冻结结果后出现晚到的奖惩写入。 */
  private void touchPlan(TrainingPlan plan) {
    if (planMapper.updateById(plan) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8104, "培训计划版本冲突，请刷新后重试");
    }
  }

  /** 沿用有符号分值；停用记录留痕但不参与综合分。 */
  private void validateFields(String type, BigDecimal score, Integer status, Long evidenceFileId) {
    if (!"REWARD".equals(type) && !"PENALTY".equals(type)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8245, "奖惩类型无效");
    }
    if (score != null && (("REWARD".equals(type) && score.signum() < 0)
        || ("PENALTY".equals(type) && score.signum() > 0))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8245, "奖惩分值方向与类型不一致");
    }
    if (status == null || (status != 0 && status != 1)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 8245, "奖惩状态无效");
    }
    if (evidenceFileId != null) {
      SysFileInfo file = fileInfoMapper.selectById(evidenceFileId);
      if (file == null || file.getObjectName() == null || file.getObjectName().isBlank()) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 8246, "奖惩佐证文件不存在");
      }
    }
  }

  private void refreshComposite(Long trainingParticipantId) {
    if (trainingParticipantId == null) {
      return;
    }
    resultSyncService.synchronizeParticipant(trainingParticipantId);
  }

  TrainingRewardPenalty requireRecord(Long id) {
    TrainingRewardPenalty record = trainingRewardPenaltyMapper.selectById(id);
    if (record == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8241, "奖惩记录不存在");
    }
    return record;
  }

  private TrainingRewardPenaltyVO toVO(TrainingRewardPenalty record) {
    TrainingRewardPenaltyVO vo = new TrainingRewardPenaltyVO();
    BeanUtils.copyProperties(record, vo);
    return vo;
  }
}

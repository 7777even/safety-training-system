package com.sts.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.mapper.SysFileInfoMapper;
import com.sts.admin.storage.StorageService;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.training.dto.TrainingTypeCreateRequest;
import com.sts.training.dto.TrainingTypeQuery;
import com.sts.training.dto.TrainingTypeUpdateRequest;
import com.sts.training.entity.TrainingType;
import com.sts.training.mapper.TrainingTypeMapper;
import com.sts.training.vo.TrainingTypeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TrainingTypeServiceImpl
  extends BaseServiceImpl<TrainingTypeMapper, TrainingType>
  implements TrainingTypeService {

  private final TrainingTypeMapper trainingTypeMapper;
  private final SysFileInfoMapper fileInfoMapper;
  private final StorageService storageService;

  @Override
  public PageResult<TrainingTypeVO> page(PageRequest<TrainingTypeQuery> request) {
    TrainingTypeQuery query = request.getQuery();
    LambdaQueryWrapper<TrainingType> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.eq(StringUtils.hasText(query.getTypeCode()), TrainingType::getTypeCode, query.getTypeCode())
        .like(StringUtils.hasText(query.getTypeName()), TrainingType::getTypeName, query.getTypeName())
        .eq(query.getStatus() != null, TrainingType::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(TrainingType::getCreateTime);
    Page<TrainingType> page = trainingTypeMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public TrainingTypeVO get(Long trainingTypeId) {
    return toVO(requireType(trainingTypeId));
  }

  @Override
  @Transactional
  public Long create(TrainingTypeCreateRequest request) {
    TrainingType type = new TrainingType();
    type.setTypeCode(request.getTypeCode().trim());
    type.setTypeName(request.getTypeName().trim());
    type.setWorkType(request.getWorkType());
    type.setTag(request.getTag());
    type.setCoverFileId(request.getCoverFileId());
    type.setInitialHours(request.getInitialHours());
    type.setReviewHours(request.getReviewHours());
    type.setOnlineMaxRatio(request.getOnlineMaxRatio());
    type.setStatus(request.getStatus());
    type.setDescription(request.getDescription());
    try {
      trainingTypeMapper.insert(type);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 8002, "同机构下培训类型编码已存在");
    }
    return type.getTrainingTypeId();
  }

  @Override
  @Transactional
  public void update(Long trainingTypeId, TrainingTypeUpdateRequest request) {
    TrainingType type = requireType(trainingTypeId);
    type.setVersion(request.getVersion());
    type.setTypeName(request.getTypeName().trim());
    type.setWorkType(request.getWorkType());
    type.setTag(request.getTag());
    type.setCoverFileId(request.getCoverFileId());
    type.setInitialHours(request.getInitialHours());
    type.setReviewHours(request.getReviewHours());
    type.setOnlineMaxRatio(request.getOnlineMaxRatio());
    type.setStatus(request.getStatus());
    type.setDescription(request.getDescription());
    if (trainingTypeMapper.updateById(type) == 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 8003, "培训类型版本冲突");
    }
  }

  @Override
  @Transactional
  public void delete(Long trainingTypeId) {
    requireType(trainingTypeId);
    this.removeById(trainingTypeId);
  }

  @Override
  public TrainingCoverFile openCover(Long trainingTypeId) {
    TrainingType type = requireType(trainingTypeId);
    return TrainingCoverSupport.open(fileInfoMapper, storageService, type.getCoverFileId());
  }

  TrainingType requireType(Long id) {
    TrainingType type = trainingTypeMapper.selectById(id);
    if (type == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 8001, "培训类型不存在");
    }
    return type;
  }

  private TrainingTypeVO toVO(TrainingType type) {
    TrainingTypeVO vo = new TrainingTypeVO();
    BeanUtils.copyProperties(type, vo);
    return vo;
  }
}

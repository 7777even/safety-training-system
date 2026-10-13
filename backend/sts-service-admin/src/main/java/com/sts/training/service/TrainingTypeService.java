package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingTypeCreateRequest;
import com.sts.training.dto.TrainingTypeQuery;
import com.sts.training.dto.TrainingTypeUpdateRequest;
import com.sts.training.vo.TrainingTypeVO;

public interface TrainingTypeService {
  PageResult<TrainingTypeVO> page(PageRequest<TrainingTypeQuery> request);
  TrainingTypeVO get(Long trainingTypeId);
  Long create(TrainingTypeCreateRequest request);
  void update(Long trainingTypeId, TrainingTypeUpdateRequest request);
  void delete(Long trainingTypeId);

  /**
   * 按类型 ID 打开当前机构培训类型封面。
   */
  TrainingCoverFile openCover(Long trainingTypeId);
}

package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingReferenceResourceQuery;
import com.sts.training.vo.TrainingReferenceResourceVO;

public interface TrainingReferenceResourceService {
  PageResult<TrainingReferenceResourceVO> page(PageRequest<TrainingReferenceResourceQuery> request);
}

package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.TrainingTeacherTaskQuery;
import com.sts.training.vo.TrainingTeacherTaskVO;

public interface TrainingTeacherTaskService {
  PageResult<TrainingTeacherTaskVO> page(PageRequest<TrainingTeacherTaskQuery> request);
}

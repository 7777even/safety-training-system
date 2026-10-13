package com.sts.training.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.training.dto.*;
import com.sts.training.vo.TrainingTemplateCategoryVO;
import com.sts.training.vo.TrainingTemplateCategoryTreeVO;

import java.util.List;

public interface TrainingTemplateCategoryService {
  PageResult<TrainingTemplateCategoryVO> page(PageRequest<TrainingTemplateCategoryQuery> request);

  List<TrainingTemplateCategoryTreeVO> tree(TrainingTemplateCategoryQuery query);
  TrainingTemplateCategoryVO get(Long templateCategoryId);
  Long create(TrainingTemplateCategoryCreateRequest request);
  void update(Long templateCategoryId, TrainingTemplateCategoryUpdateRequest request);
  void delete(Long templateCategoryId);
}

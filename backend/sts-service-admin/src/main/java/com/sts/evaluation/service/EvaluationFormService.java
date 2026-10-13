package com.sts.evaluation.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.evaluation.dto.*;
import com.sts.evaluation.vo.EvaluationFormVO;
import com.sts.evaluation.vo.EvaluationFormVersionDetailVO;
import com.sts.evaluation.vo.EvaluationFormVersionSummaryVO;

import java.util.List;

public interface EvaluationFormService {
  PageResult<EvaluationFormVO> page(PageRequest<EvaluationFormQuery> request);
  EvaluationFormVO get(Long evaluationFormId);
  Long create(EvaluationFormCreateRequest request);
  void update(Long evaluationFormId, EvaluationFormUpdateRequest request);
  void delete(Long evaluationFormId);
  EvaluationFormVersionDetailVO getVersionDetail(Long formVersionId);

  List<EvaluationFormVersionSummaryVO> listVersionSummaries(Long evaluationFormId);
  EvaluationFormVersionDetailVO publishNewVersion(Long evaluationFormId, EvaluationFormPublishRequest request);
}

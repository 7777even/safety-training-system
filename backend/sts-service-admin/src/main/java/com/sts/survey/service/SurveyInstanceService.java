package com.sts.survey.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.survey.dto.SurveyInstanceCreateRequest;
import com.sts.survey.dto.SurveyInstanceQuery;
import com.sts.survey.dto.SurveySubmitRequest;
import com.sts.survey.vo.SurveyFillVO;
import com.sts.survey.vo.SurveyInstanceVO;
import com.sts.survey.vo.SurveyStatsVO;

public interface SurveyInstanceService {
  PageResult<SurveyInstanceVO> page(PageRequest<SurveyInstanceQuery> request);

  SurveyInstanceVO get(Long surveyInstanceId);
  SurveyInstanceVO create(SurveyInstanceCreateRequest request);
  void submit(Long surveyInstanceId, SurveySubmitRequest request);

  SurveyFillVO getFill(Long surveyInstanceId);
  SurveyStatsVO stats(Long surveyInstanceId);
}

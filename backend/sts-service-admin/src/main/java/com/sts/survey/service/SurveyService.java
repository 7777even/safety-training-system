package com.sts.survey.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.survey.dto.*;
import com.sts.survey.vo.SurveyPreviewVO;
import com.sts.survey.vo.SurveyVO;
import com.sts.survey.vo.SurveyVersionVO;

public interface SurveyService {
  PageResult<SurveyVO> page(PageRequest<SurveyQuery> request);
  SurveyVO get(Long surveyId);
  Long create(SurveyCreateRequest request);
  void update(Long surveyId, SurveyUpdateRequest request);
  void delete(Long surveyId);
  void replaceQuestions(Long surveyId, SurveyQuestionsReplaceRequest request);

  /**
   * 复制为未投放草稿。
   */
  Long copy(Long surveyId, SurveyCopyRequest request);

  /**
   * 电脑或手机预览，题目内容相同。
   */
  SurveyPreviewVO preview(Long surveyId, String client);

  /**
   * 投放时内部冻结问卷快照；不作为独立版本管理入口。
   */
  SurveyVersionVO publish(Long surveyId, SurveyPublishRequest request);
}

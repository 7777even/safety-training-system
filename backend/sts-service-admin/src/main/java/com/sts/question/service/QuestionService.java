package com.sts.question.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.question.dto.*;
import com.sts.question.vo.*;

import java.util.List;

public interface QuestionService {
  PageResult<QuestionVO> page(PageRequest<QuestionQuery> request);

  QuestionVO get(Long questionId);

  Long create(QuestionCreateRequest request);

  void updateContent(Long questionId, QuestionUpdateRequest request);

  void publish(Long questionId);

  void delete(Long questionId);

  void replaceAssets(Long questionId, List<QuestionAssetRequest> assets);

  QuestionImportResultVO importQuestions(Long questionBankId, QuestionImportRequest request);

  QuestionUserStateVO upsertUserState(QuestionUserStateUpsertRequest request);
}

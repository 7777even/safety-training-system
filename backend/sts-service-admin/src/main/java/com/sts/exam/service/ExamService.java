package com.sts.exam.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.exam.dto.*;
import com.sts.exam.vo.ExamCandidateVO;
import com.sts.exam.vo.ExamVO;

import java.util.List;

public interface ExamService {
  PageResult<ExamVO> page(PageRequest<ExamQuery> request);

  ExamVO get(Long examId);

  Long create(ExamCreateRequest request);

  void update(Long examId, ExamUpdateRequest request);

  void delete(Long examId);

  void publish(Long examId);

  /**
   * 发布考试；extraPolicy 仅培训生成传入指纹/抽题覆盖项，管理端发布传 null。
   */
  void publish(Long examId, java.util.Map<String, Object> extraPolicy);

  List<ExamCandidateVO> bindSelection(Long examId, BindSelectionRequest request);

  java.util.List<com.sts.exam.vo.ExamReviewerVO> listReviewers(Long examId);

  void replaceReviewers(Long examId, ExamReviewersReplaceRequest request);

  PageResult<ExamCandidateVO> pageCandidates(Long examId, PageRequest<ExamCandidateQuery> request);
}

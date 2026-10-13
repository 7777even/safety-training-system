package com.sts.exam.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.exam.dto.ExamAttemptRecordQuery;
import com.sts.exam.dto.ExamReviewTaskQuery;
import com.sts.exam.vo.ExamAttemptRecordVO;
import com.sts.exam.vo.ExamReviewTaskVO;

public interface ExamAttemptQueryService {
  PageResult<ExamReviewTaskVO> pageReviewTasks(PageRequest<ExamReviewTaskQuery> request);

  PageResult<ExamAttemptRecordVO> pageAttempts(PageRequest<ExamAttemptRecordQuery> request);
}

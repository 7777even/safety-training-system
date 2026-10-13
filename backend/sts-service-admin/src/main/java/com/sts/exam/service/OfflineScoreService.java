package com.sts.exam.service;

import com.sts.exam.dto.OfflineScoresRequest;
import com.sts.exam.vo.OfflineScoreRecordResult;

/**
 * 线下考试成绩录入。
 * <p>
 * 线下考试没有在线作答环节，成绩由管理端录入，并作为学员端展示成绩与合格状态的唯一来源。
 */
public interface OfflineScoreService {

  /**
   * 批量录入线下考试成绩；任一记录不合法时整批拒绝。
   *
   * @param examId  线下考试 ID
   * @param request 成绩记录列表
   * @return 录入结果
   */
  OfflineScoreRecordResult record(Long examId, OfflineScoresRequest request);
}

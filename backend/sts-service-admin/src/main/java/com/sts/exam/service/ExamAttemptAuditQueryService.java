package com.sts.exam.service;

import com.sts.exam.vo.ExamAnticheatEventVO;
import com.sts.exam.vo.ExamScorePublishLogVO;

import java.util.List;

/**
 * 作答维度的追溯读取：防作弊事件与成绩发布流水。两者均为追加型事实，只读不改。
 */
public interface ExamAttemptAuditQueryService {

  List<ExamAnticheatEventVO> listAnticheatEvents(Long examAttemptId);

  List<ExamScorePublishLogVO> listScorePublishLogs(Long examAttemptId);
}

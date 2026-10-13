package com.sts.exam.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 考情人员明细；时间和客户端为最近正式作答，成绩为最终有效成绩。 */
public record ExamStatisticsPersonVO(Long examCandidateId, Long userId, String learnerName, String learnerCode,
  String candidateStatus, BigDecimal finalScore, Integer rank, boolean attended, boolean completed, boolean absent,
  LocalDateTime startedTime, LocalDateTime submittedTime, Integer usedSeconds,
  String platform, String operatingSystem, String browser) {}

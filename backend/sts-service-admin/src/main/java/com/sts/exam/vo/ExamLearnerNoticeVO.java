package com.sts.exam.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 本人考前须知安全摘要，不携带管理字段、名单或试题。
 * @param examId 考试编号
 * @param examName 考试名称
 * @param examType 考试类型
 * @param openStartTime 开放开始时间
 * @param openEndTime 开放结束时间
 * @param durationSeconds 作答时长秒数
 * @param passRatio 合格比例
 * @param passScore 合格分数
 * @param isNoticeEnabled 是否启用须知
 * @param noticeContent 须知正文
 */
public record ExamLearnerNoticeVO(Long examId, String examName, String examType, LocalDateTime openStartTime,
  LocalDateTime openEndTime, Integer durationSeconds, BigDecimal passRatio, BigDecimal passScore,
  Integer isNoticeEnabled, String noticeContent) { }

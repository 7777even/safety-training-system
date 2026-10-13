package com.sts.training.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 培训理论考试在正式开始前展示的冻结须知摘要。 */
@Data
public class TrainingLearnerExamNoticeVO {
  /** 计划专属考试 ID。 */
  private Long examId;
  /** 考试名称。 */
  private String examName;
  /** 考试类型。 */
  private String examType;
  /** 开放开始时间。 */
  private LocalDateTime openStartTime;
  /** 开放结束时间。 */
  private LocalDateTime openEndTime;
  /** 正式作答时长，单位秒。 */
  private Integer durationSeconds;
  /** 及格比例。 */
  private BigDecimal passRatio;
  /** 及格分数。 */
  private BigDecimal passScore;
  /** 是否启用自定义考试须知；1 是，0 否。 */
  private Integer isNoticeEnabled;
  /** 自定义考试须知内容。 */
  private String noticeContent;
}

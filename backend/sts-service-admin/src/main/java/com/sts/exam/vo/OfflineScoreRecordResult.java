package com.sts.exam.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线下考试成绩录入结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineScoreRecordResult {

  /** 考试 ID。 */
  private Long examId;

  /** 成功写入的成绩条数。 */
  private Integer recordedCount;

  /** 未处理的条数；整批校验通过时恒为 0。 */
  private Integer skippedCount;
}

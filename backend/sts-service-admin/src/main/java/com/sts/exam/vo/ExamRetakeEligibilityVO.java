package com.sts.exam.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前考生相对当前考试的补考资格摘要，由服务端按真实策略和作答事实计算。
 */
@Data
public class ExamRetakeEligibilityVO {
  /** 是否满足开补考的全部业务条件。 */
  private Boolean eligible;
  /** 未满足条件的中文说明；资格通过时为空。 */
  private List<String> reasons;
  /** 已使用的非作废补考次数，不含首次考试。 */
  private Integer usedRetakeCount;
  /** 策略配置的最大补考次数，不含首次考试。 */
  private Integer maxRetakeCount;
}

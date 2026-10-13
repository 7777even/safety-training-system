package com.sts.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 线下考试成绩录入的单条记录。
 */
@Data
public class OfflineScoreRecordRequest {

  /** 考生记录 ID；必须属于本场考试。 */
  @NotNull(message = "考生记录 ID 不能为空")
  private Long examCandidateId;

  /** 最终成绩；须落在 [0, 本场满分] 区间。 */
  @NotNull(message = "成绩不能为空")
  private BigDecimal finalScore;

  /** 备注，仅管理端留痕，不展示给学员。 */
  private String remark;
}

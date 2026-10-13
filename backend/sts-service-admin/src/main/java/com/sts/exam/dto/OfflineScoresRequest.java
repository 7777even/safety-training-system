package com.sts.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 线下考试成绩批量录入请求。
 * <p>
 * 任一记录不合法时整批拒绝，不做部分写入，避免出现半场成绩。
 */
@Data
public class OfflineScoresRequest {

  /** 成绩记录列表。 */
  @NotEmpty(message = "成绩列表不能为空")
  @Valid
  private List<OfflineScoreRecordRequest> scores;
}

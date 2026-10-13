package com.sts.survey.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投放时内部冻结的问卷快照，不对外作为版本管理资源。
 */
@Data
public class SurveyVersionVO {
  private Long surveyVersionId;
  private Long surveyId;
  private Integer versionNo;
  private String snapshotHash;
  private String versionNote;
  private LocalDateTime publishedTime;
}

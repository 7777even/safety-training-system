package com.sts.evaluation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EvaluationFormVersionDetailVO {
  private Long formVersionId;
  private Long evaluationFormId;
  private Integer versionNo;
  private BigDecimal totalScore;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
  private List<EvaluationItemVO> items;
  private List<EvaluationScoreRuleVO> scoreRules;
}

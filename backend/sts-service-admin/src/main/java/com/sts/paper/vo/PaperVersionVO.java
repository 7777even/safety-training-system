package com.sts.paper.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PaperVersionVO {
  private Long paperVersionId;
  private Long paperId;
  private Integer versionNo;
  private Integer durationSeconds;
  private BigDecimal totalScore;
  private BigDecimal passRatio;
  private BigDecimal passScore;
  private Integer questionCount;
  private String questionOrderMode;
  private String multiSelectScoring;
  private String drawSettingMode;
  private Long randomSeed;
  private String publishedStatus;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String versionNote;
  private List<PaperVersionQuestionVO> questions;
  private List<PaperDrawRuleVO> drawRules;
}

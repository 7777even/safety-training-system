package com.sts.paper.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PaperPreviewVO {
  private Long paperId;
  private String paperType;
  private BigDecimal totalScore;
  private BigDecimal passScore;
  private Integer questionCount;
  private List<PaperVersionQuestionVO> questions;
}

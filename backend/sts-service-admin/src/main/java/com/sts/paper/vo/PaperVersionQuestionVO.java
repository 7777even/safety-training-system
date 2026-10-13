package com.sts.paper.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaperVersionQuestionVO {
  private Long paperQuestionId;
  private Long questionId;
  private Long questionVersionId;
  private String questionType;
  private BigDecimal score;
  private Integer sortOrder;
  private Long sourceBankId;
}

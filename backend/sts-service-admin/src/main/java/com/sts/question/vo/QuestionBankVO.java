package com.sts.question.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionBankVO {
  private Long questionBankId;
  private Long organizationId;
  private String bankCode;
  private String bankName;
  private Long bankCategoryId;
  private String difficulty;
  private String questionOrderMode;
  private String archiveStatus;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  private List<Long> knowledgePointIds;
}

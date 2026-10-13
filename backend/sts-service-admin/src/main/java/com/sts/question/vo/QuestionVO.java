package com.sts.question.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionVO {
  private Long questionId;
  private Long organizationId;
  private Long questionBankId;
  private String questionCode;
  private Integer currentVersionNo;
  private String archiveStatus;
  private Integer status;
  private Integer version;
  private LocalDateTime createTime;
  private QuestionVersionVO currentVersion;
  private List<Long> knowledgePointIds;
}

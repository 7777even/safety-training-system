package com.sts.evaluation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EvaluationRecordVO {
  private Long evaluationRecordId;
  private Long organizationId;
  private Long formVersionId;
  private String targetType;
  private Long targetId;
  /** 评价人由专家查课提交接口从当前登录态写入。 */
  private Long evaluatorUserId;
  private BigDecimal totalScore;
  private String recordStatus;
  private LocalDateTime submittedTime;
  /** 记录摘要，专家查课详情只读展示。 */
  private String summary;
  private Integer version;
  private List<EvaluationRecordItemVO> items;
}

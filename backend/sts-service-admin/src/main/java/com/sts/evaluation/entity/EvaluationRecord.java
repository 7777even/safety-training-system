package com.sts.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_evaluation_record")
public class EvaluationRecord extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long evaluationRecordId;
  private Long organizationId;
  private Long formVersionId;
  private String targetType;
  private Long targetId;
  private Long evaluatorUserId;
  private BigDecimal totalScore;
  private String recordStatus;
  private LocalDateTime submittedTime;
  private String summary;
}

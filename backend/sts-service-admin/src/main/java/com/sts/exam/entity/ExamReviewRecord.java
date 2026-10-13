package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_review_record")
public class ExamReviewRecord extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long reviewRecordId;
  private Long organizationId;
  private Long examAttemptId;
  private Long examAnswerId;
  private Long reviewerUserId;
  private BigDecimal beforeScore;
  private BigDecimal afterScore;
  private String reviewComment;
  private String reviewAction;
  private LocalDateTime reviewedTime;
}

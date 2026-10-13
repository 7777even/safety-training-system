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
@TableName("sts_exam_score_publish_log")
public class ExamScorePublishLog extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long scorePublishLogId;
  private Long organizationId;
  private Long examId;
  private Long examAttemptId;
  private Long publishedBy;
  private LocalDateTime publishedTime;
  private String publishType;
  private BigDecimal scoreSnapshot;
  private String resultSnapshot;
}

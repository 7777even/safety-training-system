package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_practice_reset")
public class ExamPracticeReset extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long practiceResetId;
  private Long organizationId;
  private Long examId;
  private Long examCandidateId;
  private Long userId;
  private String resetScope;
  private LocalDateTime resetTime;
  private String reason;
}

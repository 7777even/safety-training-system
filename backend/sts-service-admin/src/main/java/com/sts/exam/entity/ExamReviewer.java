package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_reviewer")
public class ExamReviewer extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long examReviewerId;
  private Long assessmentExecutionId;
  private Long reviewerUserId;
  private String assignmentSource;
}

package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_attempt_option")
public class ExamAttemptOption extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long attemptOptionId;
  private Long attemptQuestionId;
  private String optionKey;
  private String optionContentSnapshot;
  private Integer isCorrectSnapshot;
  private Integer sortOrder;
}

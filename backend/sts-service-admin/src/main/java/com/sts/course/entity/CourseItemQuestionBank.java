package com.sts.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_course_item_question_bank")
public class CourseItemQuestionBank extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long itemQuestionBankId;
  private Long courseItemId;
  private Long questionBankId;
}

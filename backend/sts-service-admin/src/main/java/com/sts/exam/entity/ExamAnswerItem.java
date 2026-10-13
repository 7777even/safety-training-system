package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_answer_item")
public class ExamAnswerItem extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long answerItemId;
  private Long examAnswerId;
  private String itemType;
  private String itemKey;
  private String itemValue;
  private Integer sortOrder;
}

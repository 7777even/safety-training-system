package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_answer_asset")
public class ExamAnswerAsset extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long answerAssetId;
  private Long examAnswerId;
  private Long fileInfoId;
  private Integer sortOrder;
}

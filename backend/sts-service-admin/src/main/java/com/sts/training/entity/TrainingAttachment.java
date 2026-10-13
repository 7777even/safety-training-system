package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_attachment")
public class TrainingAttachment extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingAttachmentId;
  private Long trainingPlanId;
  private Long planStageId;
  private String attachmentType;
  private Long fileInfoId;
  private String attachmentName;
  private String description;
  private Integer sortOrder;
  private Integer status;
}

package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_question_asset")
public class QuestionAsset extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long questionAssetId;
  private Long questionVersionId;
  private Long assetId;
  private String assetRole;
  private String optionKey;
  private Integer sortOrder;
}

package com.sts.evaluation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_evaluation_record_item")
public class EvaluationRecordItem extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long recordItemId;
  private Long evaluationRecordId;
  private Long evaluationItemId;
  private BigDecimal score;
  private String textValue;
  private String optionValue;
  private String calculationSourceJson;
}

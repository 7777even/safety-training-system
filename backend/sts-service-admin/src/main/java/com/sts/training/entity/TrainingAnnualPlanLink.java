package com.sts.training.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.*;
/** 计划关联的年度文件快照，不随年度文件修改漂移。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("sts_training_annual_plan_link")
public class TrainingAnnualPlanLink extends BaseEntity {
  /** 主键。 */ @TableId(type=IdType.AUTO) private Long linkId;
  /** 机构。 */ private Long organizationId;
  /** 培训计划。 */ private Long trainingPlanId;
  /** 原年度计划，解除时为空。 */ @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long annualPlanId;
  /** 年度快照。 */ @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer planYear;
  /** 名称快照。 */ @TableField(updateStrategy=FieldStrategy.ALWAYS) private String planName;
  /** 文件快照。 */ @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long fileInfoId;
  /** 文件名称。 */ @TableField(updateStrategy=FieldStrategy.ALWAYS) private String fileName;
}

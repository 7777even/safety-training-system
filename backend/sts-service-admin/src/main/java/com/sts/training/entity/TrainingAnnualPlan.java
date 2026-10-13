package com.sts.training.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.*;
/** 公司年度计划及原始文件。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("sts_training_annual_plan")
public class TrainingAnnualPlan extends BaseEntity {
  /** 主键。 */ @TableId(type=IdType.AUTO) private Long annualPlanId;
  /** 机构。 */ private Long organizationId;
  /** 年度。 */ private Integer planYear;
  /** 年度计划名称。 */ private String planName;
  /** 上传文件。 */ private Long fileInfoId;
  /** 文件名称快照。 */ private String fileName;
}

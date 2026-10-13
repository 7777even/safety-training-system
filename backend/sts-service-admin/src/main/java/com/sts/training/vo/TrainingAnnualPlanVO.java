package com.sts.training.vo;
import lombok.Data;
/** 年度文件信息。 */
@Data public class TrainingAnnualPlanVO {
  /** 主键。 */ private Long annualPlanId;
  /** 年度。 */ private Integer planYear;
  /** 年度计划名称。 */ private String planName;
  /** 上传文件。 */ private Long fileInfoId;
  /** 文件名称快照。 */ private String fileName;
  /** 记录版本。 */ private Integer version;
  /** 创建时间。 */ private java.time.LocalDateTime createTime;
}

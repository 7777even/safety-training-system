package com.sts.training.vo;
import lombok.Data;
import java.math.BigDecimal;
/** 岗位培训矩阵列表与来源展示，不暴露内部审计字段。 */
@Data public class TrainingMatrixVO {
  /** 要求ID。 */ private Long matrixId;
  /** 岗位ID。 */ private Long positionId;
  /** 岗位名称。 */ private String positionName;
  /** 培训内容。 */ private String contentName;
  /** 课程ID。 */ private Long courseId;
  /** 课程版本ID。 */ private Long courseVersionId;
  /** 课程名称。 */ private String courseName;
  /** 要求学时。 */ private BigDecimal requiredHours;
  /** 启用状态。 */ private Integer status;
  /** 乐观锁版本。 */ private Integer version;
}

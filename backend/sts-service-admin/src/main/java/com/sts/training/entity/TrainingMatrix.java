package com.sts.training.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.*;
import java.math.BigDecimal;
/** 岗位培训要求；引用不可变课程版本，编辑不影响已有计划。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("sts_training_matrix")
public class TrainingMatrix extends BaseEntity {
  /** 矩阵要求ID。 */ @TableId(type=IdType.AUTO) private Long matrixId;
  /** 所属机构。 */ private Long organizationId;
  /** 适用岗位。 */ private Long positionId;
  /** 保存时岗位名称。 */ private String positionName;
  /** 培训内容。 */ private String contentName;
  /** 课程引用。 */ private Long courseId;
  /** 课程不可变版本引用。 */ private Long courseVersionId;
  /** 保存时课程名称。 */ private String courseName;
  /** 要求学时。 */ private BigDecimal requiredHours;
  /** 启用状态。 */ private Integer status;
}

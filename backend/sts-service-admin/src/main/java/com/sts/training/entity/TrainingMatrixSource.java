package com.sts.training.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.*;
/** 创建计划时的矩阵来源快照，仅追加，不随矩阵编辑。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("sts_training_matrix_source")
public class TrainingMatrixSource extends BaseEntity {
  /** 快照ID。 */ @TableId(type=IdType.AUTO) private Long sourceId;
  /** 所属机构。 */ private Long organizationId;
  /** 目标计划。 */ private Long trainingPlanId;
  /** 完整来源要求列表JSON。 */ private String snapshotJson;
}

package com.sts.training.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sts.common.entity.BaseEntity;
import lombok.*;
/** 独立变更及审批记录，原计划在批准前保持有效。 */
@Data @EqualsAndHashCode(callSuper=true) @TableName("sts_training_plan_change")
public class TrainingPlanChange extends BaseEntity {
  /** 变更主键。 */ @TableId(type=IdType.AUTO) private Long changeId;
  /** 所属机构。 */ private Long organizationId;
  /** 生效计划。 */ private Long trainingPlanId;
  /** 基准版本。 */ private Integer baseVersion;
  /** 提交人。 */ private Long submitterUserId;
  /** 指定审批人。 */ private Long approverUserId;
  /** DRAFT/PENDING/APPROVED/REJECTED。 */ private String changeStatus;
  /** 变更内容。 */ private String requestJson;
  /** 基准计划快照。 */ private String beforeSnapshotJson;
  /** 原参训名单快照。 */ private String beforeParticipantsJson;
  /** 决定意见。 */ private String decisionComment;
}

package com.sts.training.vo;
import com.sts.training.dto.TrainingPlanChangeRequest;
import lombok.Data;
/** 变更详情和审批状态，包含基准快照供核对。 */
@Data public class TrainingPlanChangeVO {
  /** 变更ID。 */ private Long changeId;
  /** 生效计划ID。 */ private Long trainingPlanId;
  /** 基准版本。 */ private Integer baseVersion;
  /** 变更记录版本。 */ private Integer version;
  /** 提交人。 */ private Long submitterUserId;
  /** 指定审批人。 */ private Long approverUserId;
  /** 变更状态。 */ private String changeStatus;
  /** 拟变更内容。 */ private TrainingPlanChangeRequest request;
  /** 原计划快照。 */ private TrainingPlanVO before;
  /** 原参训名单。 */ private java.util.List<TrainingParticipantVO> beforeParticipants;
  /** 审批意见。 */ private String decisionComment;
  /** 创建时间。 */ private java.time.LocalDateTime createTime;
}

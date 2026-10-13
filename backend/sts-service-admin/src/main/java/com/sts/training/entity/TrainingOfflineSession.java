package com.sts.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_training_offline_session")
public class TrainingOfflineSession extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long offlineSessionId;
  private Long trainingPlanId;
  private Long planStageId;
  private Long planContentId;
  private String sessionName;
  private Long teacherUserId;
  private String location;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private String sessionStatus;
  private Integer status;
}

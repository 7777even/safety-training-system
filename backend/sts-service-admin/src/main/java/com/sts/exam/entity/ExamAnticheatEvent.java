package com.sts.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_exam_anticheat_event")
public class ExamAnticheatEvent extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long anticheatEventId;
  private Long organizationId;
  private Long examAttemptId;
  private String eventType;
  private LocalDateTime eventTime;
  private Integer eventCount;
  private String actionTaken;
  private Long evidenceFileId;
  private String clientPayloadJson;
}

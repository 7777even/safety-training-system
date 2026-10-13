package com.sts.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sts_archive_participant")
public class ArchiveParticipant {
  @TableId(type = IdType.AUTO)
  private Long archiveParticipantId;
  private Long archiveVersionId;
  private Long trainingParticipantId;
  private Long userId;
  private Long participantOrgId;
  private String memberSnapshotJson;
  private String learningSnapshotJson;
  private String examSnapshotJson;
  private String evaluationSnapshotJson;
  private String rewardPenaltySnapshotJson;
  private String certificateSnapshotJson;
  private BigDecimal finalScore;
  private String resultStatus;
  private Long createBy;
  private LocalDateTime createTime;
}

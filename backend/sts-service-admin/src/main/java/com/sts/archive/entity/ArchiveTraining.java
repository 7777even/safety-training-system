package com.sts.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_archive_training")
public class ArchiveTraining extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long trainingArchiveId;
  private Long organizationId;
  private String archiveCode;
  private Long trainingPlanId;
  private Integer currentVersionNo;
  private String archiveStatus;
  private Long sealedBy;
  private LocalDateTime sealedTime;
  private Integer status;
}

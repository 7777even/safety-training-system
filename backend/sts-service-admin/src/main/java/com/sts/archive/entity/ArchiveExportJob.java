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
@TableName("sts_archive_export_job")
public class ArchiveExportJob extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long archiveExportJobId;
  private Long organizationId;
  private Long archiveVersionId;
  private String exportScope;
  private Long scopeId;
  private String exportFormat;
  private String jobStatus;
  private Long resultFileId;
  private String failureReason;
  private Long requestedBy;
  private LocalDateTime finishedTime;
}

package com.sts.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sts_archive_version")
public class ArchiveVersion {
  @TableId(type = IdType.AUTO)
  private Long archiveVersionId;
  private Long trainingArchiveId;
  private Integer versionNo;
  private String planSnapshotJson;
  private String organizationSnapshotJson;
  private String summarySnapshotJson;
  /** 当前归档版本生成文件的受控存储记录。 */
  private Long fileInfoId;
  private String snapshotHash;
  private LocalDateTime generatedTime;
  private Long generatedBy;
  private String versionReason;
  private Long createBy;
  private LocalDateTime createTime;
}

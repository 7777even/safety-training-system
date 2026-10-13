package com.sts.archive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sts_archive_asset")
public class ArchiveAsset {
  @TableId(type = IdType.AUTO)
  private Long archiveAssetId;
  private Long archiveVersionId;
  private Long sourceAttachmentId;
  private String assetType;
  private Long fileInfoId;
  private String fileNameSnapshot;
  private String checksum;
  private Integer sortOrder;
  private Long createBy;
  private LocalDateTime createTime;
}

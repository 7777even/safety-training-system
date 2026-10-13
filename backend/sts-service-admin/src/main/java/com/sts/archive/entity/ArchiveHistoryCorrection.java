package com.sts.archive.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 不可覆盖的更正账本，Mapper只提供追加与查询。 */
@Data @TableName("sts_archive_history_correction")
public class ArchiveHistoryCorrection {
  /** 追加记录主键。 */
  @TableId(type = IdType.AUTO) private Long correctionId;
  /** 原记录所属机构。 */
  private Long organizationId;
  /** 原记录所属学员。 */
  private Long userId;
  /** 来源领域类型。 */
  private String recordType;
  /** 来源记录主键。 */
  private Long recordId;
  /** 从1递增的展示版本。 */
  private Integer version;
  /** 本次字段在当前展示版本的前值。 */
  private String beforeJson;
  /** 本次允许字段的后值。 */
  private String afterJson;
  /** 非空理由。 */
  private String reason;
  /** 当前授权操作人。 */
  private Long operatorId;
  /** 操作人姓名快照。 */
  private String operatorName;
  /** 追加时间。 */
  private LocalDateTime operatedTime;
}

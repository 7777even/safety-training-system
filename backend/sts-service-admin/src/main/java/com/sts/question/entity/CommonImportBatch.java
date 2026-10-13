package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseEntity;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_common_import_batch")
public class CommonImportBatch extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long importBatchId;
  private Long organizationId;
  private String businessType;
  private Long businessId;
  private Long sourceFileId;
  private String templateType;
  private String importStatus;
  private Integer totalRows;
  private Integer successRows;
  private Integer failureRows;
  private Long resultFileId;
  private LocalDateTime startedTime;
  private LocalDateTime finishedTime;
}

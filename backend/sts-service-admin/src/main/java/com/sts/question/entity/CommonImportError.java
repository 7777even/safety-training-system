package com.sts.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.sts.common.entity.BaseAuditEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_common_import_error")
public class CommonImportError extends BaseAuditEntity {
  @TableId(type = IdType.AUTO)
  private Long importErrorId;
  private Long importBatchId;
  private Integer rowNo;
  private String fieldName;
  private String errorCode;
  private String errorMessage;
  private String rawDataJson;
}

package com.sts.ops.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 【暂时隐藏】原运营异步导出任务中心；当前中山50最小闭环使用同步 CSV，不读写本表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_ops_export_job")
public class OpsExportJob extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long exportJobId;
  private Long organizationId;
  private String exportDomain;
  private String exportFormat;
  private String requestJson;
  private String jobStatus;
  private Long resultFileId;
  private String failureReason;
  private Long requestedBy;
  private LocalDateTime finishedTime;
}

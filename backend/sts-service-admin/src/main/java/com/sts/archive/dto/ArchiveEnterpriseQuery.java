package com.sts.archive.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业档案筛选：按企业名称与培训计划字段查询已结束培训。
 */
@Data
public class ArchiveEnterpriseQuery {
  /**
   * 企业名称，按当前租户机构名称匹配。
   */
  private String organizationName;
  /**
   * 培训计划名称关键字。
   */
  private String planName;
  /**
   * 培训计划编号。
   */
  private String planCode;
  /**
   * 培训类型 ID。
   */
  private Long trainingTypeId;
  /**
   * 计划开始时间下限。
   */
  private LocalDateTime startTimeFrom;
  /**
   * 计划开始时间上限。
   */
  private LocalDateTime startTimeTo;
  /**
   * 计划结束时间下限。
   */
  private LocalDateTime endTimeFrom;
  /**
   * 计划结束时间上限。
   */
  private LocalDateTime endTimeTo;
}

package com.sts.regulator.vo;

import lombok.Data;

/**
 * 跨租户聚合的单个租户行。
 */
@Data
public class TenantAggregateItemVO {

  /**
   * 企业租户 ID。
   */
  private Long organizationId;

  /**
   * 企业租户编码。
   */
  private String orgCode;

  /**
   * 租户类型：ENTERPRISE 或 TRAINING_INSTITUTION。
   */
  private String orgType;

  /**
   * 该租户已发布课程数。
   */
  private Long publishedCourseCount;

  /**
   * 该租户运行中培训计划数。
   */
  private Long runningPlanCount;

  /**
   * 该租户参训人员总数。
   */
  private Long participantCount;

  /**
   * 该租户已完成参训人员数。
   */
  private Long completedParticipantCount;
}

package com.sts.training.port;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 培训域请求课程域创建或复用开课的命令。使用已冻结的 courseVersionId，不得改读课程当前版本。
 */
@Data
public class TrainingCourseOfferingCommand {
  /**
   * 计划所属机构 ID。
   */
  private Long organizationId;
  /**
   * 培训计划内容 ID，作为开课幂等键。
   */
  private Long planContentId;
  /**
   * 课程 ID。
   */
  private Long courseId;
  /**
   * 发布时冻结的课程版本 ID。
   */
  private Long courseVersionId;
  /**
   * 开课名称。
   */
  private String offeringName;
  /**
   * 开课开始时间（阶段时间，缺失时为计划时间）。
   */
  private LocalDateTime startTime;
  /**
   * 开课结束时间（阶段时间，缺失时为计划时间）。
   */
  private LocalDateTime endTime;
  /**
   * 完成规则（ALL_REQUIRED_ITEMS/ALL_ITEMS）。
   */
  private String completionRule;
  /**
   * 是否必修，决定开课类型 REQUIRED/ELECTIVE。
   */
  private boolean required;
}

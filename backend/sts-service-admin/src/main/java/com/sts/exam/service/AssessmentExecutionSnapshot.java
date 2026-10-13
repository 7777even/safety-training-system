package com.sts.exam.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 考核执行不可变快照。所有来源、资源和执行规则统一使用该结构持久化。
 */
public record AssessmentExecutionSnapshot(
  int schemaVersion,
  Long organizationId,
  Source source,
  String assessmentKind,
  Resource resource,
  Rules rules
) {

  /**
   * 考核执行的稳定业务来源。
   */
  public record Source(
    String type,
    Long businessId,
    Long examId,
    Long courseOfferingId,
    Long courseVersionId,
    Long courseItemId
  ) {
  }

  /**
   * 考核执行冻结的题库或试卷资源。
   */
  public record Resource(String type, Long id, String name) {
  }

  /**
   * 考核执行冻结的基础规则和场景扩展策略。
   */
  public record Rules(
    Integer durationSeconds,
    String totalScore,
    String passScore,
    String questionOrderMode,
    String multiSelectScoring,
    JsonNode policy
  ) {
  }
}

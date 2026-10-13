package com.sts.certificate.service;

/**
 * 学员达到独立课程、独立考试或培训结果合格标准时，按启用规则幂等自动签发证书。
 *
 * <p>本服务位于证书域，只依赖证书域自身组件，供课程、考试、培训各业务域在各自结果收口处调用，
 * 不反向依赖任何业务域实体。
 */
public interface CertificateAutoIssueService {

  /**
   * 按来源类型与主数据标识触发自动签发。
   *
   * @param sourceType     来源类型：{@code COURSE} 独立课程、{@code EXAM} 独立理论考试、{@code TRAINING} 培训结果
   * @param sourceId       来源主数据标识：课程 {@code courseId}、考试 {@code examId}、培训计划 {@code trainingPlanId}
   * @param userId         证书持有人用户标识
   * @param sourceResultId 来源结果记录标识，可为空
   */
  void issueForSource(String sourceType, Long sourceId, Long userId, Long sourceResultId);
}

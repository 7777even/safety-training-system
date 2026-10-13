package com.sts.regulation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.regulation.dto.WarningQuery;
import com.sts.regulation.service.RegulationLearningStatsService;
import com.sts.regulation.service.RegulationTrainingRecordService;
import com.sts.regulation.service.RegulationWorkbenchService;
import com.sts.regulation.service.WarningService;
import com.sts.regulation.vo.WarningVO;
import com.sts.regulator.vo.LearningLearnerRowVO;
import com.sts.regulator.vo.LearningOfferingRowVO;
import com.sts.regulator.vo.LearningStatsVO;
import com.sts.regulator.vo.RegulationWorkbenchVO;
import com.sts.training.dto.TrainingPlanQuery;
import com.sts.training.vo.TrainingParticipantResultsVO;
import com.sts.training.vo.TrainingParticipantVO;
import com.sts.training.vo.TrainingPlanVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 园区监管工作台、学习统计、学时预警与培训只读下钻。
 */
@RestController
@RequiredArgsConstructor
public class RegulationController {

  private final RegulationWorkbenchService workbenchService;
  private final RegulationLearningStatsService learningStatsService;
  private final WarningService warningService;
  private final RegulationTrainingRecordService trainingRecordService;

  /**
   * 园区监管工作台概览。
   */
  @GetMapping("/regulation/workbench")
  @SaCheckPermission("regulation:overview:view")
  public RegulationWorkbenchVO workbench() {
    return workbenchService.workbench();
  }

  /**
   * 导出受监管机构统计 CSV。
   */
  @GetMapping("/regulation/institutions/export")
  @SaCheckPermission("regulation:learning:export")
  public ResponseEntity<byte[]> exportInstitutions() {
    return workbenchService.exportInstitutions();
  }

  /**
   * 学习情况统计，不传 organizationId 为园区汇总。
   */
  @GetMapping("/regulation/learning-stats")
  @SaCheckPermission("regulation:learning:view")
  public LearningStatsVO learningStats(@RequestParam(required = false) Long organizationId) {
    return learningStatsService.stats(organizationId);
  }

  /**
   * 上课人数下钻。
   */
  @GetMapping("/regulation/learning-stats/learners")
  @SaCheckPermission("regulation:learning:view")
  public List<LearningLearnerRowVO> learningLearners(@RequestParam(required = false) Long organizationId) {
    return learningStatsService.learners(organizationId);
  }

  /**
   * 参与课程下钻。
   */
  @GetMapping("/regulation/learning-stats/offerings")
  @SaCheckPermission("regulation:learning:view")
  public List<LearningOfferingRowVO> learningOfferings(@RequestParam(required = false) Long organizationId) {
    return learningStatsService.offerings(organizationId);
  }

  /**
   * 导出学习情况统计 CSV。
   */
  @GetMapping("/regulation/learning-stats/export")
  @SaCheckPermission("regulation:learning:export")
  public ResponseEntity<byte[]> exportLearningStats(@RequestParam(required = false) Long organizationId) {
    return learningStatsService.export(organizationId);
  }

  /**
   * 待查看学时预警数量。
   */
  @GetMapping("/regulation/warnings/unread-count")
  @SaCheckPermission("regulation:warning:list")
  public long unreadWarningCount() {
    return warningService.unreadCount();
  }

  /**
   * 分页查询学时预警。
   */
  @GetMapping("/regulation/warnings")
  @SaCheckPermission("regulation:warning:list")
  public PageResult<WarningVO> pageWarnings(PageRequest<WarningQuery> request) {
    return warningService.page(request);
  }

  /**
   * 预警详情；待查看时标记已查看。
   */
  @GetMapping("/regulation/warnings/{warningId}")
  @SaCheckPermission("regulation:warning:list")
  public WarningVO getWarning(@PathVariable Long warningId) {
    return warningService.get(warningId);
  }

  /**
   * 标记预警为已查看。
   */
  @PostMapping("/regulation/warnings/{warningId}/view")
  @SaCheckPermission("regulation:warning:action")
  public void view(@PathVariable Long warningId) {
    warningService.markRead(warningId, StpUtil.getLoginIdAsLong());
  }

  /**
   * 按当前筛选导出学时预警 CSV。
   */
  @GetMapping("/regulation/warnings/export")
  @SaCheckPermission("regulation:warning:export")
  public ResponseEntity<byte[]> exportWarnings(WarningQuery query) {
    return warningService.export(query);
  }

  /**
   * 分页查询目标企业培训计划。
   */
  @GetMapping("/regulation/training-plans")
  @SaCheckPermission("regulation:overview:view")
  public PageResult<TrainingPlanVO> pageTrainingPlans(PageRequest<TrainingPlanQuery> request) {
    return trainingRecordService.pagePlans(request);
  }

  /**
   * 查询目标企业培训计划详情。
   */
  @GetMapping("/regulation/training-plans/{trainingPlanId}")
  @SaCheckPermission("regulation:overview:view")
  public TrainingPlanVO getTrainingPlan(@PathVariable Long trainingPlanId) {
    return trainingRecordService.getPlan(trainingPlanId);
  }

  /**
   * 查询目标企业计划参训人。
   */
  @GetMapping("/regulation/training-plans/{trainingPlanId}/participants")
  @SaCheckPermission("regulation:overview:view")
  public List<TrainingParticipantVO> listTrainingParticipants(
    @PathVariable Long trainingPlanId) {
    return trainingRecordService.listParticipants(trainingPlanId);
  }

  /**
   * 查询目标企业参训人内容结果。
   */
  @GetMapping("/regulation/training-plans/{trainingPlanId}/participants/{trainingParticipantId}/results")
  @SaCheckPermission("regulation:overview:view")
  public TrainingParticipantResultsVO getTrainingResults(
    @PathVariable Long trainingPlanId, @PathVariable Long trainingParticipantId) {
    return trainingRecordService.getResults(trainingPlanId, trainingParticipantId);
  }
}

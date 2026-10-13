package com.sts.regulation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.regulation.dto.WarningQuery;
import com.sts.regulation.entity.RegulationWarning;
import com.sts.regulation.entity.RegulationWarningEvidence;
import com.sts.regulation.export.Utf8CsvExporter;
import com.sts.regulation.mapper.RegulationWarningEvidenceMapper;
import com.sts.regulation.mapper.RegulationWarningMapper;
import com.sts.regulation.vo.WarningVO;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WarningServiceImpl implements WarningService {

  private static final Set<String> VISIBLE = Set.of(
    PlanHoursWarningRefreshService.STATUS_UNREAD,
    PlanHoursWarningRefreshService.STATUS_READ);

  private final RegulatorIdentityGuard regulatorIdentityGuard;
  private final RegulationWarningMapper warningMapper;
  private final RegulationWarningEvidenceMapper evidenceMapper;
  private final TrainingPlanMapper trainingPlanMapper;
  private final ObjectMapper objectMapper;

  @Override
  public long unreadCount() {
    regulatorIdentityGuard.requireRegulator();
    Long count = warningMapper.selectCount(
      new LambdaQueryWrapper<RegulationWarning>()
        .eq(RegulationWarning::getWarningStatus, PlanHoursWarningRefreshService.STATUS_UNREAD));
    return count == null ? 0 : count;
  }

  @Override
  public PageResult<WarningVO> page(PageRequest<WarningQuery> request) {
    regulatorIdentityGuard.requireRegulator();
    WarningQuery query = request.getQuery() != null ? request.getQuery() : new WarningQuery();
    LambdaQueryWrapper<RegulationWarning> wrapper = new LambdaQueryWrapper<RegulationWarning>()
      .eq(query.getRegulatedOrgId() != null, RegulationWarning::getRegulatedOrgId, query.getRegulatedOrgId())
      .orderByDesc(RegulationWarning::getDetectedTime);
    if (StringUtils.hasText(query.getWarningStatus())) {
      wrapper.eq(RegulationWarning::getWarningStatus, query.getWarningStatus());
    } else {
      wrapper.in(RegulationWarning::getWarningStatus, VISIBLE);
    }
    if (query.getTrainingTypeId() != null) {
      List<Long> planIds = TenantContext.callWithBypass(() -> trainingPlanMapper.selectList(
          new LambdaQueryWrapper<TrainingPlan>()
            .eq(TrainingPlan::getTrainingTypeId, query.getTrainingTypeId())
            .select(TrainingPlan::getTrainingPlanId))
        .stream()
        .map(TrainingPlan::getTrainingPlanId)
        .toList());
      if (planIds.isEmpty()) {
        return new PageResult<>(List.of(), 0);
      }
      wrapper.in(RegulationWarning::getTrainingPlanId, planIds);
    }
    Page<RegulationWarning> page = warningMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  @Transactional
  public WarningVO get(Long warningId) {
    regulatorIdentityGuard.requireRegulator();
    RegulationWarning warning = requireWarning(warningId);
    if (PlanHoursWarningRefreshService.STATUS_UNREAD.equals(warning.getWarningStatus())
      || "OPEN".equals(warning.getWarningStatus())) {
      markReadInternal(warning);
    }
    return toVO(warning);
  }

  @Override
  @Transactional
  public void markRead(Long warningId, Long viewerUserId) {
    regulatorIdentityGuard.requireRegulator();
    RegulationWarning warning = requireWarning(warningId);
    markReadInternal(warning);
  }

  @Override
  public ResponseEntity<byte[]> export(WarningQuery query) {
    PageRequest<WarningQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(Utf8CsvExporter.MAX_ROWS + 1);
    request.setQuery(query);
    PageResult<WarningVO> page = page(request);
    List<List<String>> rows = new ArrayList<>();
    for (WarningVO vo : page.list()) {
      rows.add(List.of(
        String.valueOf(vo.getWarningId()),
        nvl(vo.getWarningStatus()),
        vo.getRegulatedOrgId() == null ? "" : String.valueOf(vo.getRegulatedOrgId()),
        nvl(vo.getRegulatedOrgName()),
        nvl(vo.getPlanCode()),
        nvl(vo.getPlanName()),
        nvl(vo.getTrainingTypeName()),
        nvl(vo.getTrainingNature()),
        decimal(vo.getPlanTotalHours()),
        decimal(vo.getRequiredHours()),
        decimal(vo.getHoursGap())
      ));
    }
    return Utf8CsvExporter.download(
      "regulation-warnings.csv",
      "status=" + (query == null || query.getWarningStatus() == null ? "UNREAD,READ" : query.getWarningStatus())
        + ";regulatedOrgId=" + (query == null ? "" : query.getRegulatedOrgId())
        + ";trainingTypeId=" + (query == null ? "" : query.getTrainingTypeId()),
      List.of("warningId", "warningStatus", "regulatedOrgId", "regulatedOrgName", "planCode", "planName",
              "trainingTypeName", "trainingNature", "planTotalHours", "requiredHours", "hoursGap"),
      rows);
  }

  private void markReadInternal(RegulationWarning warning) {
    if (PlanHoursWarningRefreshService.STATUS_READ.equals(warning.getWarningStatus())
      || PlanHoursWarningRefreshService.STATUS_ENDED.equals(warning.getWarningStatus())) {
      return;
    }
    warning.setWarningStatus(PlanHoursWarningRefreshService.STATUS_READ);
    if (warning.getFirstViewedTime() == null) {
      warning.setFirstViewedTime(LocalDateTime.now());
    }
    warningMapper.updateById(warning);
  }

  private RegulationWarning requireWarning(Long warningId) {
    RegulationWarning warning = warningMapper.selectOne(
      new LambdaQueryWrapper<RegulationWarning>().eq(RegulationWarning::getWarningId, warningId));
    if (warning == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9802, "预警不存在");
    }
    return warning;
  }

  private WarningVO toVO(RegulationWarning warning) {
    WarningVO vo = new WarningVO();
    BeanUtils.copyProperties(warning, vo);
    RegulationWarningEvidence evidence = evidenceMapper.selectOne(
      new LambdaQueryWrapper<RegulationWarningEvidence>()
        .eq(RegulationWarningEvidence::getWarningId, warning.getWarningId())
        .eq(RegulationWarningEvidence::getMetricCode, PlanHoursWarningRefreshService.METRIC_CODE)
        .last("LIMIT 1"));
    if (evidence != null && StringUtils.hasText(evidence.getEvidenceSnapshotJson())) {
      fillEvidence(vo, evidence.getEvidenceSnapshotJson());
    }
    return vo;
  }

  private void fillEvidence(WarningVO vo, String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      vo.setPlanCode(text(root, "planCode"));
      vo.setPlanName(text(root, "planName"));
      vo.setRegulatedOrgName(text(root, "regulatedOrgName"));
      vo.setTrainingTypeName(text(root, "trainingTypeName"));
      vo.setTrainingNature(text(root, "trainingNature"));
      if (root.hasNonNull("trainingTypeId")) {
        vo.setTrainingTypeId(root.get("trainingTypeId").asLong());
      }
      vo.setPlanTotalHours(decimalNode(root, "planTotalHours"));
      vo.setRequiredHours(decimalNode(root, "requiredHours"));
      vo.setHoursGap(decimalNode(root, "hoursGap"));
    } catch (Exception ignored) {
      // 历史证据无法解析时仍返回预警主记录
    }
  }

  private static String text(JsonNode root, String field) {
    return root.hasNonNull(field) ? root.get(field).asText() : null;
  }

  private static BigDecimal decimalNode(JsonNode root, String field) {
    return root.hasNonNull(field) ? new BigDecimal(root.get(field).asText()) : null;
  }

  private static String decimal(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}

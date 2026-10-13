package com.sts.regulation.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.regulation.export.Utf8CsvExporter;
import com.sts.regulator.mapper.RegulatorAggregateMapper;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.regulator.vo.InstitutionSummaryVO;
import com.sts.regulator.vo.RegulationWorkbenchVO;
import com.sts.regulator.vo.SpecialOperationStatsVO;
import com.sts.regulator.vo.WorkbenchShortcutVO;
import com.sts.regulator.vo.WorkbenchTotalsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 园区监管工作台：合并原 overview 与 aggregates 口径。
 */
@Service
@RequiredArgsConstructor
public class RegulationWorkbenchService {

  private final RegulatorIdentityGuard regulatorIdentityGuard;
  private final RegulatorAggregateMapper aggregateMapper;

  public RegulationWorkbenchVO workbench() {
    regulatorIdentityGuard.requireRegulator();
    RegulationWorkbenchVO vo = new RegulationWorkbenchVO();
    WorkbenchTotalsVO totals = aggregateMapper.selectWorkbenchTotals();
    if (totals != null) {
      vo.setEnterpriseCount(totals.getEnterpriseCount());
      vo.setTrainingInstitutionCount(totals.getTrainingInstitutionCount());
      vo.setPlanTotalCount(totals.getPlanTotalCount());
      vo.setRunningPlanCount(totals.getRunningPlanCount());
      vo.setEndedPlanCount(totals.getEndedPlanCount());
      vo.setParticipantCount(totals.getParticipantCount());
      vo.setCompletedParticipantCount(totals.getCompletedParticipantCount());
    }
    vo.setUnreadWarningCount(aggregateMapper.selectUnreadWarningCount());
    vo.setTrainingTypeDistribution(nullToEmpty(aggregateMapper.selectTrainingTypeDistribution()));
    SpecialOperationStatsVO special = aggregateMapper.selectSpecialOperationStats();
    vo.setSpecialOperation(special == null ? new SpecialOperationStatsVO() : special);
    vo.setInstitutions(nullToEmpty(aggregateMapper.selectInstitutionSummaries()));
    vo.setShortcuts(shortcuts());
    return vo;
  }

  public ResponseEntity<byte[]> exportInstitutions() {
    regulatorIdentityGuard.requireRegulator();
    List<InstitutionSummaryVO> rows = nullToEmpty(aggregateMapper.selectInstitutionSummaries());
    List<List<String>> data = new ArrayList<>();
    for (InstitutionSummaryVO row : rows) {
      data.add(List.of(
        String.valueOf(row.getOrganizationId()),
        nvl(row.getOrgCode()),
        nvl(row.getOrgName()),
        nvl(row.getOrgType()),
        String.valueOf(row.getPlanCount()),
        String.valueOf(row.getParticipantCount())
      ));
    }
    return Utf8CsvExporter.download(
      "regulation-institutions.csv",
      "scope=all-regulated",
      List.of("organizationId", "orgCode", "orgName", "orgType", "planCount", "participantCount"),
      data);
  }

  private List<WorkbenchShortcutVO> shortcuts() {
    // 只放监管侧能真正打开的只读下钻入口：课程中心页依赖 coursecategory:list 等业务按钮权限，
    // 监管角色按其固定权限集（RegulatorRolePermissions）拿不到，点了必然 403，故不提供该入口。
    return List.of(
      shortcut("CERTIFICATE", "证书管理", "/admin/result/certificate/certificates", "certificate:cert:list"),
      shortcut("DATA_CENTER", "数据中心", "/admin/regulation/learning-stats", "regulation:learning:view")
    );
  }

  private WorkbenchShortcutVO shortcut(String code, String title, String path, String permission) {
    return new WorkbenchShortcutVO(code, title, path, permission, StpUtil.hasPermission(permission));
  }

  private static <T> List<T> nullToEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }

  private static String nvl(String value) {
    return value == null ? "" : value;
  }
}

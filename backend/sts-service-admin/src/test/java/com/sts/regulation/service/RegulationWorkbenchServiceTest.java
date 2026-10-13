package com.sts.regulation.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.regulator.mapper.RegulatorAggregateMapper;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.regulator.vo.RegulationWorkbenchVO;
import com.sts.regulator.vo.WorkbenchTotalsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RegulationWorkbenchServiceTest {

  private RegulatorAggregateMapper aggregateMapper;
  private RegulationWorkbenchService service;
  private MockedStatic<StpUtil> stp;

  @BeforeEach
  void setUp() {
    aggregateMapper = mock(RegulatorAggregateMapper.class);
    service = new RegulationWorkbenchService(new RegulatorIdentityGuard(), aggregateMapper);
    stp = mockStatic(StpUtil.class);
  }

  @AfterEach
  void tearDown() {
    stp.close();
  }

  @Test
  @DisplayName("非监管身份拒绝工作台")
  void workbench_rejectsNonRegulator() {
    stp.when(StpUtil::isLogin).thenReturn(true);
    SaSession session = new SaSession("t");
    session.set(TenantSessionKeys.ORG_TYPE, "ENTERPRISE");
    stp.when(StpUtil::getTokenSession).thenReturn(session);

    assertThatThrownBy(() -> service.workbench())
      .hasFieldOrPropertyWithValue("code", 4065);
  }

  @Test
  @DisplayName("工作台汇总口径并按权限标记快捷入口")
  void workbench_mapsTotalsAndShortcutGrant() {
    loginRegulator();
    stp.when(() -> StpUtil.hasPermission("certificate:cert:list")).thenReturn(true);
    stp.when(() -> StpUtil.hasPermission("course:course:list")).thenReturn(false);
    stp.when(() -> StpUtil.hasPermission("regulation:learning:view")).thenReturn(true);

    WorkbenchTotalsVO totals = new WorkbenchTotalsVO();
    totals.setEnterpriseCount(3);
    totals.setTrainingInstitutionCount(1);
    totals.setPlanTotalCount(9);
    when(aggregateMapper.selectWorkbenchTotals()).thenReturn(totals);
    when(aggregateMapper.selectUnreadWarningCount()).thenReturn(2L);
    when(aggregateMapper.selectTrainingTypeDistribution()).thenReturn(List.of());
    when(aggregateMapper.selectSpecialOperationStats()).thenReturn(null);
    when(aggregateMapper.selectInstitutionSummaries()).thenReturn(List.of());

    RegulationWorkbenchVO vo = service.workbench();
    assertThat(vo.getEnterpriseCount()).isEqualTo(3);
    assertThat(vo.getTrainingInstitutionCount()).isEqualTo(1);
    assertThat(vo.getPlanTotalCount()).isEqualTo(9);
    assertThat(vo.getUnreadWarningCount()).isEqualTo(2);
    assertThat(vo.getShortcuts()).extracting(com.sts.regulator.vo.WorkbenchShortcutVO::isGranted)
      .containsExactly(true, true);
    assertThat(vo.getShortcuts()).extracting(com.sts.regulator.vo.WorkbenchShortcutVO::getPath)
      .containsExactly("/admin/result/certificate/certificates", "/admin/regulation/learning-stats");
  }

  private void loginRegulator() {
    stp.when(StpUtil::isLogin).thenReturn(true);
    SaSession session = new SaSession("t");
    session.set(TenantSessionKeys.ORG_TYPE, "REGULATOR");
    stp.when(StpUtil::getTokenSession).thenReturn(session);
  }
}

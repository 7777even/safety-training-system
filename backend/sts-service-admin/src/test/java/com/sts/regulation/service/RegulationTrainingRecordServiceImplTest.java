package com.sts.regulation.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantContext;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.training.dto.TrainingPlanQuery;
import com.sts.training.service.TrainingPlanService;
import com.sts.training.service.TrainingResultSyncService;
import com.sts.training.vo.TrainingParticipantResultsVO;
import com.sts.training.vo.TrainingParticipantVO;
import com.sts.training.vo.TrainingPlanVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RegulationTrainingRecordServiceImplTest {

  private final RegulatorIdentityGuard guard = new RegulatorIdentityGuard();
  private final TrainingPlanService planService = mock(TrainingPlanService.class);
  private final TrainingResultSyncService resultSyncService = mock(TrainingResultSyncService.class);
  private final RegulationTrainingRecordServiceImpl service =
    new RegulationTrainingRecordServiceImpl(guard, planService, resultSyncService);

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("已切换视角时返回目标企业计划分页")
  void pagePlans_switchedView_delegates() {
    PageRequest<TrainingPlanQuery> request = new PageRequest<>();
    PageResult<TrainingPlanVO> expected = new PageResult<>(List.of(new TrainingPlanVO()), 1L);
    when(planService.page(request)).thenReturn(expected);

    try (MockedStatic<StpUtil> stp = mockRegulator()) {
      TenantContext.setTenantId(2L);
      assertThat(service.pagePlans(request)).isSameAs(expected);
    }
  }

  @Test
  @DisplayName("未切换视角返回 400/9860")
  void pagePlans_sameTenant_rejected() {
    try (MockedStatic<StpUtil> stp = mockRegulator()) {
      TenantContext.setTenantId(1L);
      assertThatThrownBy(() -> service.pagePlans(new PageRequest<>()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 9860)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
      verify(planService, never()).page(any());
    }
  }

  @Test
  @DisplayName("非监管租户返回 403/4065")
  void pagePlans_enterprise_rejected() {
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::isLogin).thenReturn(true);
      SaSession session = new SaSession("t");
      session.set(TenantSessionKeys.ORG_TYPE, "ENTERPRISE");
      session.set(TenantSessionKeys.ORGANIZATION_ID, 2L);
      stp.when(StpUtil::getTokenSession).thenReturn(session);
      TenantContext.setTenantId(2L);

      assertThatThrownBy(() -> service.pagePlans(new PageRequest<>()))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("code", 4065);
      verify(planService, never()).page(any());
    }
  }

  @Test
  @DisplayName("已切换视角时返回参训人结果")
  void getResults_switchedView_delegates() {
    TrainingParticipantResultsVO expected = new TrainingParticipantResultsVO();
    when(resultSyncService.getResults(10L, 20L)).thenReturn(expected);
    try (MockedStatic<StpUtil> stp = mockRegulator()) {
      TenantContext.setTenantId(2L);
      assertThat(service.getResults(10L, 20L)).isSameAs(expected);
      verify(resultSyncService).getResults(10L, 20L);
    }
  }

  @Test
  @DisplayName("已切换视角时返回计划详情与参训人")
  void getPlanAndParticipants_switchedView_delegates() {
    TrainingPlanVO plan = new TrainingPlanVO();
    when(planService.get(10L)).thenReturn(plan);
    when(planService.listParticipants(10L)).thenReturn(List.of(new TrainingParticipantVO()));
    try (MockedStatic<StpUtil> stp = mockRegulator()) {
      TenantContext.setTenantId(2L);
      assertThat(service.getPlan(10L)).isSameAs(plan);
      assertThat(service.listParticipants(10L)).hasSize(1);
    }
  }

  private MockedStatic<StpUtil> mockRegulator() {
    MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
    stp.when(StpUtil::isLogin).thenReturn(true);
    SaSession session = new SaSession("t");
    session.set(TenantSessionKeys.ORG_TYPE, "REGULATOR");
    session.set(TenantSessionKeys.ORGANIZATION_ID, 1L);
    stp.when(StpUtil::getTokenSession).thenReturn(session);
    return stp;
  }
}

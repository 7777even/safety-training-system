package com.sts.regulation.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.exception.BusinessException;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.regulator.mapper.RegulatorAggregateMapper;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.regulator.vo.LearningStatsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RegulationLearningStatsServiceTest {

  private RegulatorAggregateMapper aggregateMapper;
  private OrganizationMapper organizationMapper;
  private RegulationLearningStatsService service;
  private MockedStatic<StpUtil> stp;

  @BeforeEach
  void setUp() {
    aggregateMapper = mock(RegulatorAggregateMapper.class);
    organizationMapper = mock(OrganizationMapper.class);
    service = new RegulationLearningStatsService(new RegulatorIdentityGuard(), aggregateMapper, organizationMapper);
    stp = mockStatic(StpUtil.class);
    stp.when(StpUtil::isLogin).thenReturn(true);
    SaSession session = new SaSession("t");
    session.set(TenantSessionKeys.ORG_TYPE, "REGULATOR");
    stp.when(StpUtil::getTokenSession).thenReturn(session);
  }

  @AfterEach
  void tearDown() {
    stp.close();
  }

  @Test
  @DisplayName("园区汇总附带机构列表，零分母指标为 0")
  void stats_parkIncludesInstitutionsAndZeroRates() {
    LearningStatsVO park = new LearningStatsVO();
    park.setClassSessionCount(0);
    park.setClassLearnerCount(0);
    park.setEmployeeAccountCount(0);
    when(aggregateMapper.selectLearningStats(null)).thenReturn(park);
    when(aggregateMapper.selectLearningStatsByInstitution()).thenReturn(List.of(new LearningStatsVO()));

    LearningStatsVO vo = service.stats(null);
    assertThat(vo.getAvgSessionsPerLearner()).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));
    assertThat(vo.getParticipationRate()).isEqualByComparingTo(BigDecimal.ZERO.setScale(4));
    assertThat(vo.getInstitutions()).hasSize(1);
  }

  @Test
  @DisplayName("非受监管机构 403")
  void stats_rejectsNonRegulatedOrg() {
    Organization org = new Organization();
    org.setOrgType("REGULATOR");
    org.setStatus(1);
    when(organizationMapper.selectById(9L)).thenReturn(org);

    assertThatThrownBy(() -> service.stats(9L))
      .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(4063));
  }
}

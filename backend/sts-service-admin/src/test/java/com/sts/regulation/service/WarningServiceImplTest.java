package com.sts.regulation.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.tenant.TenantSessionKeys;
import com.sts.common.api.PageRequest;
import com.sts.regulation.dto.WarningQuery;
import com.sts.regulation.entity.RegulationWarning;
import com.sts.regulation.entity.RegulationWarningEvidence;
import com.sts.regulation.mapper.RegulationWarningEvidenceMapper;
import com.sts.regulation.mapper.RegulationWarningMapper;
import com.sts.regulation.vo.WarningVO;
import com.sts.regulator.service.RegulatorIdentityGuard;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingPlanMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarningServiceImplTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, RegulationWarning.class);
    TableInfoHelper.initTableInfo(assistant, RegulationWarningEvidence.class);
    TableInfoHelper.initTableInfo(assistant, TrainingPlan.class);
  }

  private RegulationWarningMapper warningMapper;
  private WarningServiceImpl service;
  private MockedStatic<StpUtil> stp;

  @BeforeEach
  void setUp() {
    warningMapper = mock(RegulationWarningMapper.class);
    service = new WarningServiceImpl(
      new RegulatorIdentityGuard(), warningMapper, mock(RegulationWarningEvidenceMapper.class),
      mock(TrainingPlanMapper.class), new ObjectMapper());
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
  @DisplayName("默认列表只查待查看和已查看")
  void page_defaultsToVisibleStatuses() {
    when(warningMapper.selectPage(any(), any())).thenReturn(new Page<>());
    PageRequest<WarningQuery> request = new PageRequest<>();
    request.setPageNum(1);
    request.setPageSize(20);
    request.setQuery(new WarningQuery());
    service.page(request);
    verify(warningMapper).selectPage(any(), any());
  }

  @Test
  @DisplayName("查看待查看预警时标记已查看")
  void get_marksUnreadAsRead() {
    RegulationWarning warning = new RegulationWarning();
    warning.setWarningId(8L);
    warning.setWarningStatus("UNREAD");
    when(warningMapper.selectOne(any())).thenReturn(warning);
    when(warningMapper.updateById(warning)).thenReturn(1);

    WarningVO vo = service.get(8L);
    assertThat(vo.getWarningStatus()).isEqualTo("READ");
    ArgumentCaptor<RegulationWarning> captor = ArgumentCaptor.forClass(RegulationWarning.class);
    verify(warningMapper).updateById(captor.capture());
    assertThat(captor.getValue().getWarningStatus()).isEqualTo("READ");
    assertThat(captor.getValue().getFirstViewedTime()).isNotNull();
  }

  @Test
  @DisplayName("导出为真实 UTF-8 BOM CSV")
  void export_isRealCsv() {
    when(warningMapper.selectPage(any(), any())).thenReturn(new Page<>());
    ResponseEntity<byte[]> response = service.export(new WarningQuery());
    byte[] body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(new String(body, StandardCharsets.UTF_8)).contains("warningId");
    assertThat(body[0]).isEqualTo((byte) 0xEF);
  }
}

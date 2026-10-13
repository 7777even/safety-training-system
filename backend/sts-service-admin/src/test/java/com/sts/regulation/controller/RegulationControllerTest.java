package com.sts.regulation.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.regulation.dto.WarningQuery;
import com.sts.regulation.service.RegulationLearningStatsService;
import com.sts.regulation.service.RegulationTrainingRecordService;
import com.sts.regulation.service.RegulationWorkbenchService;
import com.sts.regulation.service.WarningService;
import com.sts.regulator.vo.LearningStatsVO;
import com.sts.regulator.vo.RegulationWorkbenchVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegulationControllerTest {

  private RegulationWorkbenchService workbenchService;
  private RegulationLearningStatsService learningStatsService;
  private WarningService warningService;
  private MockMvc mvc;
  private MockedStatic<StpUtil> stp;

  @BeforeEach
  void setUp() {
    workbenchService = mock(RegulationWorkbenchService.class);
    learningStatsService = mock(RegulationLearningStatsService.class);
    warningService = mock(WarningService.class);
    mvc = MockMvcBuilders.standaloneSetup(new RegulationController(
        workbenchService, learningStatsService, warningService, mock(RegulationTrainingRecordService.class)))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .build();
    stp = mockStatic(StpUtil.class);
    stp.when(StpUtil::getLoginIdAsLong).thenReturn(99L);
  }

  @AfterEach
  void tearDown() {
    stp.close();
  }

  @Test
  @DisplayName("工作台委托 Service")
  void workbench_delegates() throws Exception {
    when(workbenchService.workbench()).thenReturn(new RegulationWorkbenchVO());
    mvc.perform(get("/regulation/workbench")).andExpect(status().isOk());
    verify(workbenchService).workbench();
  }

  @Test
  @DisplayName("学习统计透传 organizationId")
  void learningStats_passesOrgId() throws Exception {
    when(learningStatsService.stats(7L)).thenReturn(new LearningStatsVO());
    mvc.perform(get("/regulation/learning-stats").param("organizationId", "7"))
      .andExpect(status().isOk());
    verify(learningStatsService).stats(7L);
  }

  @Test
  @DisplayName("预警分页绑定 query")
  void pageWarnings_bindsQuery() throws Exception {
    mvc.perform(get("/regulation/warnings")
                  .param("pageNum", "1")
                  .param("pageSize", "20")
                  .param("query.warningStatus", "UNREAD"))
      .andExpect(status().isOk());
    verify(warningService).page(any(PageRequest.class));
  }

  @Test
  @DisplayName("标记已查看透传路径 ID")
  void view_passesWarningId() throws Exception {
    mvc.perform(post("/regulation/warnings/11/view")).andExpect(status().isOk());
    verify(warningService).markRead(eq(11L), eq(99L));
  }

  @Test
  @DisplayName("学习统计导出返回原始 CSV 响应")
  void exportLearning_returnsCsv() throws Exception {
    when(learningStatsService.export(isNull())).thenReturn(ResponseEntity.ok(new byte[]{1, 2}));
    mvc.perform(get("/regulation/learning-stats/export")).andExpect(status().isOk());
    verify(learningStatsService).export(isNull());
  }

  @Test
  @DisplayName("预警导出绑定筛选")
  void exportWarnings_bindsQuery() throws Exception {
    when(warningService.export(any(WarningQuery.class))).thenReturn(ResponseEntity.ok(new byte[0]));
    mvc.perform(get("/regulation/warnings/export").param("regulatedOrgId", "2"))
      .andExpect(status().isOk());
    verify(warningService).export(any(WarningQuery.class));
  }
}

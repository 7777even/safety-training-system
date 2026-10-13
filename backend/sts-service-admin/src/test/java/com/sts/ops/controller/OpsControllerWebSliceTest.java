package com.sts.ops.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sts.admin.display.advice.ResultEasyTransAdvice;
import com.sts.ops.service.OpsWorkbenchService;
import com.sts.ops.vo.OpsWorkbenchSummaryVO;
import io.github.easytrans.core.spi.TranslationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpsControllerWebSliceTest {

  private OpsWorkbenchService workbenchService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    workbenchService = mock(OpsWorkbenchService.class);
    TranslationExecutor translationExecutor = mock(TranslationExecutor.class);
    mockMvc = MockMvcBuilders
      .standaloneSetup(new OpsController(workbenchService))
      .setControllerAdvice(new ResultEasyTransAdvice(translationExecutor, new ObjectMapper()))
      .build();
  }

  @Test
  @DisplayName("企业工作台响应统一包装为 Result")
  void enterpriseWorkbench_isWrappedIntoResult() throws Exception {
    OpsWorkbenchSummaryVO summary = new OpsWorkbenchSummaryVO();
    summary.setOpenTodoCount(3);
    when(workbenchService.enterpriseSummary()).thenReturn(summary);

    mockMvc.perform(get("/ops/workbench/enterprise"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.openTodoCount").value(3));
  }
}

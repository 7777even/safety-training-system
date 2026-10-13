package com.sts.regulation.controller;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.regulation.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegulationControllerTrainingRecordTest {

  @Test
  void pagePlans_delegatesToRecordService() throws Exception {
    RegulationTrainingRecordService recordService = mock(RegulationTrainingRecordService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(recordService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .build();

    mvc.perform(get("/regulation/training-plans").param("pageNum", "1").param("pageSize", "20"))
      .andExpect(status().isOk());

    verify(recordService).pagePlans(any(PageRequest.class));
  }

  @Test
  void getResults_passesPathIds() throws Exception {
    RegulationTrainingRecordService recordService = mock(RegulationTrainingRecordService.class);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(recordService)).build();

    mvc.perform(get("/regulation/training-plans/10/participants/20/results"))
      .andExpect(status().isOk());

    verify(recordService).getResults(10L, 20L);
  }

  private RegulationController controller(RegulationTrainingRecordService recordService) {
    return new RegulationController(
      mock(RegulationWorkbenchService.class),
      mock(RegulationLearningStatsService.class),
      mock(WarningService.class),
      recordService);
  }
}

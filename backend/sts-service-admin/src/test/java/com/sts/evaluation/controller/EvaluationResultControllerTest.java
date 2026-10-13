package com.sts.evaluation.controller;

import com.sts.evaluation.service.EvaluationRecordService;
import com.sts.evaluation.service.ExpertInspectionService;
import com.sts.evaluation.vo.EvaluationRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收口超范围评价入口后，只保留记录查询和专家查课。
 */
class EvaluationResultControllerTest {

  private EvaluationRecordService evaluationRecordService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    evaluationRecordService = mock(EvaluationRecordService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(
      new EvaluationResultController(evaluationRecordService, mock(ExpertInspectionService.class))).build();
  }

  @Test
  @DisplayName("GET /evaluation/records/{id}：仍委托查询")
  void getRecord_delegates() throws Exception {
    EvaluationRecordVO vo = new EvaluationRecordVO();
    vo.setEvaluationRecordId(9L);
    when(evaluationRecordService.get(9L)).thenReturn(vo);

    mockMvc.perform(get("/evaluation/records/9"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.evaluationRecordId").value(9));
  }

  @Test
  @DisplayName("通用评价创建/提交与计算批次路径不再暴露")
  void outOfScopeEvaluationEntries_unmapped() throws Exception {
    mockMvc.perform(post("/evaluation/records")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"formVersionId\":1,\"targetType\":\"COURSE\",\"targetId\":1}"))
      .andExpect(status().isNotFound());
    mockMvc.perform(post("/evaluation/records/9/submit")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
      .andExpect(status().isNotFound());
    mockMvc.perform(post("/evaluation/calculation-runs/calculate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
      .andExpect(status().isNotFound());
    mockMvc.perform(post("/evaluation/calculation-runs/1/confirm")).andExpect(status().isNotFound());
    mockMvc.perform(get("/evaluation/calculation-runs")).andExpect(status().isNotFound());
    mockMvc.perform(get("/evaluation/calculation-runs/1/sources")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("专家查课提交与计划内查询仍可达")
  void expertInspection_mapped() throws Exception {
    mockMvc.perform(post("/evaluation/expert-inspections/submit")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                        "{\"trainingPlanId\":1,\"planContentId\":10,\"formVersionId\":3,\"items\":[{\"evaluationItemId\":11,\"score\":90}]}"))
      .andExpect(status().isOk());
    mockMvc.perform(get("/training/plans/1/expert-inspections")).andExpect(status().isOk());
  }
}

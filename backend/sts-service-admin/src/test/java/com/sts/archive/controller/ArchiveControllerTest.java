package com.sts.archive.controller;

import com.sts.archive.service.ArchiveCertificateGuard;
import com.sts.archive.service.ArchiveService;
import com.sts.archive.vo.ArchiveViewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 档案公开入口保留一期/企业/个人查询；版本历史和导出任务中心不可达。
 * <p>证书清单必须按 {@code certificate:cert:list} 门控，缺权限时整字段不返回。
 */
class ArchiveControllerTest {

  private ArchiveService archiveService;
  private ArchiveCertificateGuard certificateGuard;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    archiveService = mock(ArchiveService.class);
    certificateGuard = mock(ArchiveCertificateGuard.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new ArchiveController(archiveService, certificateGuard,
      mock(com.sts.learner.service.LearnerHistoryService.class))).build();
  }

  @Test
  @DisplayName("档案版本与导出任务路径不再暴露")
  void versionAndExportEntries_unmapped() throws Exception {
    mockMvc.perform(get("/archive/trainings/1/versions")).andExpect(status().isNotFound());
    mockMvc.perform(get("/archive/versions/1")).andExpect(status().isNotFound());
    mockMvc.perform(get("/archive/export-jobs")).andExpect(status().isNotFound());
    mockMvc.perform(post("/archive/export-jobs")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
      .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /archive/trainings/{id}：委托一期一档查询并传递证书权限")
  void getByPlan_delegates() throws Exception {
    ArchiveViewVO vo = new ArchiveViewVO();
    vo.setTrainingPlanId(8L);
    when(certificateGuard.canListCertificates()).thenReturn(true);
    when(archiveService.getByPlan(8L, true)).thenReturn(vo);
    mockMvc.perform(get("/archive/trainings/8"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.trainingPlanId").value(8));
    verify(archiveService).getByPlan(8L, true);
  }

  @Test
  @DisplayName("缺证书权限时响应不含证书清单字段")
  void getByPlan_withoutCertificatePermission_omitsCertificates() throws Exception {
    ArchiveViewVO vo = new ArchiveViewVO();
    vo.setTrainingPlanId(8L);
    vo.setStatistics(java.util.Map.of("participantCount", 3L));
    when(certificateGuard.canListCertificates()).thenReturn(false);
    when(archiveService.getByPlan(8L, false)).thenReturn(vo);

    mockMvc.perform(get("/archive/trainings/8"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.certificates").doesNotExist())
      .andExpect(jsonPath("$.statistics.participantCount").value(3));
  }

  @Test
  @DisplayName("GET /archive/enterprises 与 /archive/persons/{id} 可达且传递证书权限")
  void enterpriseAndPerson_mapped() throws Exception {
    when(archiveService.getPerson(3L, false)).thenReturn(new com.sts.archive.vo.ArchivePersonVO());
    when(archiveService.listEnterprise(any(), eq(false))).thenReturn(List.of());
    mockMvc.perform(get("/archive/enterprises").param("organizationName", "示范"))
      .andExpect(status().isOk());
    mockMvc.perform(get("/archive/persons/3")).andExpect(status().isOk());
    verify(archiveService).listEnterprise(any(), eq(false));
    verify(archiveService).getPerson(3L, false);
  }
}

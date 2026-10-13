package com.sts.org.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.PageRequestArgumentResolver;
import com.sts.org.service.OrganizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationControllerTest {

  private OrganizationService service;
  private MockMvc mvc;
  private MockedStatic<StpUtil> stp;

  @BeforeEach
  void setUp() {
    service = mock(OrganizationService.class);
    mvc = MockMvcBuilders.standaloneSetup(new OrganizationController(service))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .build();
    stp = mockStatic(StpUtil.class);
    stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
  }

  @AfterEach
  void tearDown() {
    stp.close();
  }

  @Test
  void pageOrganizations_bindsQueryAndDelegates() throws Exception {
    mvc.perform(get("/org/organizations")
                  .param("pageNum", "2")
                  .param("pageSize", "30")
                  .param("query.keyword", "示范")
                  .param("query.orgType", "ENTERPRISE")
                  .param("query.status", "1"))
      .andExpect(status().isOk());

    verify(service).pageOrganizations(eq(7L), argThat(request ->
                                                        request.getPageNum() == 2 && request.getPageSize() == 30
                                                          && "示范".equals(request.getQuery().getKeyword())
                                                          && "ENTERPRISE".equals(request.getQuery().getOrgType())
                                                          && Integer.valueOf(1).equals(request.getQuery().getStatus())));
  }

  @Test
  void getOrganization_passesPathVariable() throws Exception {
    mvc.perform(get("/org/organizations/{organizationId}", 21L))
      .andExpect(status().isOk());

    verify(service).getOrganization(7L, 21L);
  }

  @Test
  void createOrganization_delegatesToService() throws Exception {
    mvc.perform(post("/org/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"orgCode\":\"ACME\",\"orgName\":\"示范企业\",\"orgType\":\"ENTERPRISE\","
                             + "\"adminPassword\":\"deadbeef\"}"))
      .andExpect(status().isOk());

    verify(service).createOrganization(eq(7L), argThat(request ->
                                                         "ACME".equals(request.getOrgCode())
                                                           && "ENTERPRISE".equals(request.getOrgType())));
  }

  @Test
  void createOrganization_invalidBodyReturns400WithoutServiceCall() throws Exception {
    mvc.perform(post("/org/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"orgCode\":\"\",\"orgName\":\"\",\"orgType\":\"UNKNOWN\"}"))
      .andExpect(status().isBadRequest());

    verify(service, never()).createOrganization(any(), any());
  }

  @Test
  void createOrganization_rejectsRemovedHierarchyType() throws Exception {
    mvc.perform(post("/org/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"orgCode\":\"PARK01\",\"orgName\":\"第一园区\",\"orgType\":\"PARK\"}"))
      .andExpect(status().isBadRequest());

    verify(service, never()).createOrganization(any(), any());
  }

  @Test
  void updateOrganization_delegatesToService() throws Exception {
    mvc.perform(put("/org/organizations/{organizationId}", 21L)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"orgName\":\"示范企业\",\"status\":0,\"version\":3}"))
      .andExpect(status().isOk());

    verify(service).updateOrganization(eq(7L), eq(21L), argThat(request ->
                                                                  "示范企业".equals(request.getOrgName())
                                                                    && Integer.valueOf(0).equals(request.getStatus())
                                                                    && Integer.valueOf(3).equals(request.getVersion())));
  }

  @Test
  void updateOrganization_invalidBodyReturns400WithoutServiceCall() throws Exception {
    mvc.perform(put("/org/organizations/{organizationId}", 21L)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"orgName\":\"\",\"status\":null,\"version\":null}"))
      .andExpect(status().isBadRequest());

    verify(service, never()).updateOrganization(any(), any(), any());
  }
}

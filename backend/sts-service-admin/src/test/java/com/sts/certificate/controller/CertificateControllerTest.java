package com.sts.certificate.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.dto.CertificateIssueRuleCreateRequest;
import com.sts.certificate.service.CertificateIssueRuleService;
import com.sts.certificate.service.CertificateService;
import com.sts.certificate.service.CertificateTemplateService;
import com.sts.certificate.vo.CertificateVO;
import com.sts.common.api.PageRequestArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 证书公开入口不再提供任意签发、撤销和重发；预览与本人证书可达。
 */
class CertificateControllerTest {

  private CertificateService certificateService;
  private CertificateIssueRuleService ruleService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    certificateService = mock(CertificateService.class);
    ruleService = mock(CertificateIssueRuleService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new CertificateController(
        mock(CertificateTemplateService.class),
        ruleService,
        certificateService))
      .setCustomArgumentResolvers(new PageRequestArgumentResolver())
      .build();
  }

  @Test
  @DisplayName("规则详情、更新和统一来源候选路径可达")
  void ruleManagement_mapped() throws Exception {
    mockMvc.perform(get("/certificate/rules/3")).andExpect(status().isOk());
    mockMvc.perform(put("/certificate/rules/3")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("""
                                 {"version":0,"ruleName":"培训合格规则","certificateTemplateId":1,
                                  "sourceType":"TRAINING","sourceId":10,"conditionType":"PASS",
                                  "isAutoIssue":1,"status":1}
                                 """))
      .andExpect(status().isOk());
    mockMvc.perform(get("/certificate/rule-source-candidates")
                      .param("pageNum", "1").param("pageSize", "20")
                      .param("query.sourceType", "COURSE"))
      .andExpect(status().isOk());
  }

  @Test
  @DisplayName("手工签发、撤销和重发路径不再暴露")
  void issueRevokeReissue_unmapped() throws Exception {
    mockMvc.perform(post("/certificate/certificates/issue")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
      .andExpect(status().isNotFound());
    mockMvc.perform(post("/certificate/certificates/1/revoke")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"reason\":\"作废\"}"))
      .andExpect(status().isNotFound());
    mockMvc.perform(post("/certificate/certificates/1/reissue")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
      .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("模板预览路径可达")
  void preview_mapped() throws Exception {
    when(certificateService.preview(2L)).thenReturn(
      new StoredDownload(new ByteArrayResource(new byte[]{1}), "preview.png", "image/png", 1));
    mockMvc.perform(get("/certificate/templates/2/preview")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("我的证书路径可达")
  void myCertificates_mapped() throws Exception {
    try (MockedStatic<StpUtil> stp = org.mockito.Mockito.mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
      mockMvc.perform(get("/certificate/my-certificates")).andExpect(status().isOk());
    }
  }

  @Test
  @DisplayName("证书公开请求和响应不声明已隐藏的条件、领取与撤销字段")
  void publicModels_excludeHiddenFields() {
    assertThat(declaredFieldNames(CertificateIssueRuleCreateRequest.class))
      .doesNotContain("conditionValue", "collectionInstruction");
    assertThat(declaredFieldNames(CertificateVO.class))
      .doesNotContain("revokedTime");
  }

  private static String[] declaredFieldNames(Class<?> modelType) {
    return Arrays.stream(modelType.getDeclaredFields())
      .map(Field::getName)
      .toArray(String[]::new);
  }
}

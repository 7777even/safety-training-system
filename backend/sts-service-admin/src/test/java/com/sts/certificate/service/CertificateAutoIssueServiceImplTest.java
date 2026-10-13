package com.sts.certificate.service;

import com.sts.certificate.dto.CertificateIssueRequest;
import com.sts.certificate.entity.CertificateIssueRule;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateIssueRuleMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateAutoIssueServiceImplTest {

  private CertificateIssueRuleMapper ruleMapper;
  private CertificateTemplateMapper templateMapper;
  private CertificateTemplateVersionMapper versionMapper;
  private CertificateService certificateService;
  private CertificateAutoIssueServiceImpl service;

  @BeforeEach
  void setUp() {
    ruleMapper = mock(CertificateIssueRuleMapper.class);
    templateMapper = mock(CertificateTemplateMapper.class);
    versionMapper = mock(CertificateTemplateVersionMapper.class);
    certificateService = mock(CertificateService.class);
    service = new CertificateAutoIssueServiceImpl(ruleMapper, templateMapper, versionMapper, certificateService);
  }

  @Test
  @DisplayName("issueForSource：三类来源都以传入的主数据标识原样签发")
  void issueForSource_passesSourceMetadataToIssue() {
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule("EXAM", 38L)));
    when(templateMapper.selectById(5L)).thenReturn(template());
    when(versionMapper.selectOne(any())).thenReturn(publishedVersion());

    service.issueForSource("EXAM", 38L, 7L, 50L);

    ArgumentCaptor<CertificateIssueRequest> captor = ArgumentCaptor.forClass(CertificateIssueRequest.class);
    verify(certificateService).issue(captor.capture());
    CertificateIssueRequest request = captor.getValue();
    assertThat(request.getSourceType()).isEqualTo("EXAM");
    assertThat(request.getSourceId()).isEqualTo(38L);
    assertThat(request.getHolderUserId()).isEqualTo(7L);
    assertThat(request.getSourceResultId()).isEqualTo(50L);
    assertThat(request.getCertificateRuleId()).isEqualTo(9L);
    assertThat(request.getCertificateTemplateId()).isEqualTo(5L);
    assertThat(request.getTemplateVersionId()).isEqualTo(55L);
  }

  @Test
  @DisplayName("issueForSource：模板停用时跳过签发")
  void issueForSource_skipsDisabledTemplate() {
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule("COURSE", 20L)));
    CertificateTemplate disabled = template();
    disabled.setStatus(0);
    when(templateMapper.selectById(5L)).thenReturn(disabled);

    service.issueForSource("COURSE", 20L, 7L, 3L);

    verify(certificateService, never()).issue(any());
  }

  @Test
  @DisplayName("issueForSource：模板缺少当前已发布版本时跳过签发")
  void issueForSource_skipsWithoutPublishedVersion() {
    when(ruleMapper.selectList(any())).thenReturn(List.of(rule("TRAINING", 1L)));
    when(templateMapper.selectById(5L)).thenReturn(template());
    when(versionMapper.selectOne(any())).thenReturn(null);

    service.issueForSource("TRAINING", 1L, 7L, 1L);

    verify(certificateService, never()).issue(any());
  }

  @Test
  @DisplayName("issueForSource：无启用规则或参数缺失时都不签发")
  void issueForSource_skipsWhenNoRuleOrBlankInput() {
    when(ruleMapper.selectList(any())).thenReturn(List.of());

    service.issueForSource("COURSE", 20L, 7L, 3L);
    service.issueForSource(null, 20L, 7L, 3L);
    service.issueForSource("COURSE", null, 7L, 3L);
    service.issueForSource("COURSE", 20L, null, 3L);

    verify(certificateService, never()).issue(any());
  }

  private static CertificateIssueRule rule(String sourceType, Long sourceId) {
    CertificateIssueRule rule = new CertificateIssueRule();
    rule.setCertificateRuleId(9L);
    rule.setCertificateTemplateId(5L);
    rule.setSourceType(sourceType);
    rule.setSourceId(sourceId);
    rule.setConditionType("PASS");
    rule.setIsAutoIssue(1);
    rule.setStatus(1);
    return rule;
  }

  private static CertificateTemplate template() {
    CertificateTemplate template = new CertificateTemplate();
    template.setCertificateTemplateId(5L);
    template.setCurrentVersionNo(1);
    template.setStatus(1);
    return template;
  }

  private static CertificateTemplateVersion publishedVersion() {
    CertificateTemplateVersion version = new CertificateTemplateVersion();
    version.setTemplateVersionId(55L);
    version.setCertificateTemplateId(5L);
    version.setVersionNo(1);
    version.setPublishedStatus("PUBLISHED");
    return version;
  }
}

package com.sts.certificate.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.certificate.dto.CertificateElementRequest;
import com.sts.certificate.dto.CertificateTemplatePublishRequest;
import com.sts.certificate.dto.CertificateTemplateUpdateRequest;
import com.sts.certificate.entity.CertificateIssueRule;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateElement;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateIssueRuleMapper;
import com.sts.certificate.mapper.CertificateTemplateElementMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import com.sts.certificate.support.CertificateMapperItConfig;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

@SpringBootTest(classes = CertificateMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CertificateTemplateServiceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CertificateTemplateServiceImpl service;
  @Autowired
  private CertificateTemplateMapper templateMapper;
  @Autowired
  private CertificateTemplateVersionMapper versionMapper;
  @Autowired
  private CertificateTemplateElementMapper elementMapper;
  @Autowired
  private CertificateIssueRuleMapper ruleMapper;

  @Test
  @DisplayName("草稿重复保存后原子发布，旧发布版本保持内容并转为归档")
  void saveAndPublish_preservesPublishedHistory() {
    CertificateTemplate template = insertTemplate("IT-DESIGN");
    CertificateTemplateVersion old = insertVersion(template.getCertificateTemplateId(), 1, "PUBLISHED", 81L);
    template.setCurrentVersionNo(1);
    templateMapper.updateById(template);
    template = templateMapper.selectById(template.getCertificateTemplateId());

    service.update(template.getCertificateTemplateId(), update(template.getVersion(), "第一次草稿"));
    CertificateTemplate afterFirst = templateMapper.selectById(template.getCertificateTemplateId());
    CertificateTemplateVersion draft = draft(template.getCertificateTemplateId());
    Long draftId = draft.getTemplateVersionId();
    assertThat(draft.getVersionNo()).isEqualTo(2);

    service.update(template.getCertificateTemplateId(), update(afterFirst.getVersion(), "第二次草稿"));
    CertificateTemplate afterSecond = templateMapper.selectById(template.getCertificateTemplateId());
    assertThat(draft(template.getCertificateTemplateId()).getTemplateVersionId()).isEqualTo(draftId);
    assertThat(elements(draftId)).extracting(CertificateTemplateElement::getStaticContent)
      .containsExactly("第二次草稿");

    CertificateTemplatePublishRequest publish = publish(afterSecond.getVersion(), "正式内容");
    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(77L);
      service.publishVersion(template.getCertificateTemplateId(), publish);
    }

    CertificateTemplate stored = templateMapper.selectById(template.getCertificateTemplateId());
    assertThat(stored.getCurrentVersionNo()).isEqualTo(2);
    assertThat(versionMapper.selectById(old.getTemplateVersionId()).getPublishedStatus()).isEqualTo("ARCHIVED");
    CertificateTemplateVersion current = versionMapper.selectById(draftId);
    assertThat(current.getPublishedStatus()).isEqualTo("PUBLISHED");
    assertThat(current.getPublishedBy()).isEqualTo(77L);
    assertThat(elements(old.getTemplateVersionId())).isEmpty();
    assertThat(elements(draftId)).extracting(CertificateTemplateElement::getStaticContent)
      .containsExactly("正式内容");
  }

  @Test
  @DisplayName("有签发规则引用的模板不可删除")
  void delete_rejectsReferencedTemplate() {
    CertificateTemplate template = insertTemplate("IT-REF");
    CertificateIssueRule rule = new CertificateIssueRule();
    rule.setRuleName("引用规则");
    rule.setCertificateTemplateId(template.getCertificateTemplateId());
    rule.setSourceType("TRAINING");
    rule.setSourceId(9001L);
    rule.setConditionType("PASS");
    rule.setIsAutoIssue(1);
    rule.setStatus(1);
    ruleMapper.insert(rule);

    assertThatThrownBy(() -> service.delete(template.getCertificateTemplateId()))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 9403);
    assertThat(templateMapper.selectById(template.getCertificateTemplateId())).isNotNull();
  }

  private CertificateTemplate insertTemplate(String code) {
    CertificateTemplate template = new CertificateTemplate();
    template.setTemplateCode(code);
    template.setTemplateName("模板-" + code);
    template.setCurrentVersionNo(0);
    template.setStatus(1);
    template.setDescription("IT");
    templateMapper.insert(template);
    return templateMapper.selectById(template.getCertificateTemplateId());
  }

  private CertificateTemplateVersion insertVersion(Long templateId, int versionNo, String status, Long backgroundId) {
    CertificateTemplateVersion version = new CertificateTemplateVersion();
    version.setCertificateTemplateId(templateId);
    version.setVersionNo(versionNo);
    version.setBackgroundFileId(backgroundId);
    version.setWidthPx(800);
    version.setHeightPx(600);
    version.setPublishedStatus(status);
    versionMapper.insert(version);
    return version;
  }

  private CertificateTemplateVersion draft(Long templateId) {
    return versionMapper.selectOne(new LambdaQueryWrapper<CertificateTemplateVersion>()
                                     .eq(CertificateTemplateVersion::getCertificateTemplateId, templateId)
                                     .eq(CertificateTemplateVersion::getPublishedStatus, "DRAFT"));
  }

  private List<CertificateTemplateElement> elements(Long versionId) {
    return elementMapper.selectList(new LambdaQueryWrapper<CertificateTemplateElement>()
                                      .eq(CertificateTemplateElement::getTemplateVersionId, versionId)
                                      .orderByAsc(CertificateTemplateElement::getSortOrder));
  }

  private static CertificateTemplateUpdateRequest update(int version, String content) {
    CertificateTemplateUpdateRequest request = new CertificateTemplateUpdateRequest();
    request.setVersion(version);
    request.setTemplateName("培训合格证");
    request.setStatus(1);
    request.setDescription("IT");
    request.setBackgroundFileId(91L);
    request.setWidthPx(800);
    request.setHeightPx(600);
    request.setElements(List.of(text(content)));
    return request;
  }

  private static CertificateTemplatePublishRequest publish(int version, String content) {
    CertificateTemplatePublishRequest request = new CertificateTemplatePublishRequest();
    request.setVersion(version);
    request.setBackgroundFileId(91L);
    request.setWidthPx(800);
    request.setHeightPx(600);
    request.setElements(List.of(text(content)));
    return request;
  }

  private static CertificateElementRequest text(String content) {
    CertificateElementRequest element = new CertificateElementRequest();
    element.setElementType("TEXT");
    element.setStaticContent(content);
    element.setXPosition(30);
    element.setYPosition(40);
    element.setFontFamily("SansSerif");
    element.setFontSize(24);
    element.setFontColor("#112233");
    element.setSortOrder(1);
    element.setStatus(1);
    return element;
  }
}

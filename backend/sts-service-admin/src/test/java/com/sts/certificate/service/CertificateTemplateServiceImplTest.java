package com.sts.certificate.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.dto.CertificateElementRequest;
import com.sts.certificate.dto.CertificateTemplatePublishRequest;
import com.sts.certificate.dto.CertificateTemplateUpdateRequest;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateElement;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateIssueRuleMapper;
import com.sts.certificate.mapper.CertificateTemplateElementMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import com.sts.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CertificateTemplateServiceImplTest {

  private CertificateTemplateMapper templateMapper;
  private CertificateTemplateVersionMapper templateVersionMapper;
  private CertificateTemplateElementMapper templateElementMapper;
  private CertificateIssueRuleMapper ruleMapper;
  private CertificateImageRenderer renderer;
  private GeneratedFileStore generatedFileStore;
  private CertificateTemplateServiceImpl service;

  @BeforeEach
  void setUp() {
    templateMapper = mock(CertificateTemplateMapper.class);
    templateVersionMapper = mock(CertificateTemplateVersionMapper.class);
    templateElementMapper = mock(CertificateTemplateElementMapper.class);
    ruleMapper = mock(CertificateIssueRuleMapper.class);
    renderer = mock(CertificateImageRenderer.class);
    generatedFileStore = mock(GeneratedFileStore.class);
    when(templateMapper.updateById(any(CertificateTemplate.class))).thenReturn(1);
    service = new CertificateTemplateServiceImpl(
      templateMapper, templateVersionMapper, templateElementMapper, ruleMapper, renderer, generatedFileStore);
  }

  @Test
  @DisplayName("openBackground：优先返回草稿底图，设计器才能看到未发布的最新底图")
  void openBackground_prefersDraftBackground() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L, 2, 3));
    CertificateTemplateVersion published = version(11L, 2, "PUBLISHED");
    published.setBackgroundFileId(9L);
    CertificateTemplateVersion draft = version(12L, 3, "DRAFT");
    draft.setBackgroundFileId(21L);
    when(templateVersionMapper.selectList(any())).thenReturn(List.of(draft, published));
    StoredDownload stored = new StoredDownload(new ByteArrayResource(new byte[]{1, 2}), "bg.png", "image/png", 2L);
    when(generatedFileStore.open(eq(21L), anyInt(), anyString())).thenReturn(stored);

    assertThat(service.openBackground(1L)).isSameAs(stored);
    verify(generatedFileStore).open(eq(21L), anyInt(), anyString());
  }

  @Test
  @DisplayName("openBackground：没有草稿时回落到当前发布版本底图")
  void openBackground_fallsBackToCurrentPublishedVersion() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L, 2, 3));
    CertificateTemplateVersion published = version(11L, 2, "PUBLISHED");
    published.setBackgroundFileId(9L);
    CertificateTemplateVersion archived = version(10L, 1, "ARCHIVED");
    archived.setBackgroundFileId(8L);
    when(templateVersionMapper.selectList(any())).thenReturn(List.of(published, archived));
    StoredDownload stored = new StoredDownload(new ByteArrayResource(new byte[]{3}), "bg.png", "image/png", 1L);
    when(generatedFileStore.open(eq(9L), anyInt(), anyString())).thenReturn(stored);

    assertThat(service.openBackground(1L)).isSameAs(stored);
    verify(generatedFileStore).open(eq(9L), anyInt(), anyString());
  }

  @Test
  @DisplayName("openBackground：从未设计过的模板直接以 9404 拒绝，不返回空底图")
  void openBackground_rejectsTemplateWithoutAnyVersion() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L, 0, 0));
    when(templateVersionMapper.selectList(any())).thenReturn(List.of());

    assertThatThrownBy(() -> service.openBackground(1L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("code", 9404);
    verifyNoInteractions(generatedFileStore);
  }

  @Test
  @DisplayName("get：同时返回当前发布版本和草稿的完整元素")
  void get_returnsPublishedAndDraftDesigns() {
    CertificateTemplate template = template(1L, 2, 3);
    when(templateMapper.selectById(1L)).thenReturn(template);
    CertificateTemplateVersion published = version(11L, 2, "PUBLISHED");
    CertificateTemplateVersion draft = version(12L, 3, "DRAFT");
    when(templateVersionMapper.selectList(any())).thenReturn(List.of(published, draft));
    CertificateTemplateElement publishedElement = element(101L, 11L, "TEXT", null, "已发布");
    CertificateTemplateElement draftElement = element(102L, 12L, "VARIABLE", "HOLDER_NAME", null);
    when(templateElementMapper.selectList(any())).thenReturn(List.of(publishedElement, draftElement));

    var result = service.get(1L);

    assertThat(result.getDescription()).isEqualTo("模板说明");
    assertThat(result.getCurrentPublishedVersion().getTemplateVersionId()).isEqualTo(11L);
    assertThat(result.getCurrentPublishedVersion().getElements()).extracting("staticContent")
      .containsExactly("已发布");
    assertThat(result.getDraftVersion().getTemplateVersionId()).isEqualTo(12L);
    assertThat(result.getDraftVersion().getElements()).extracting("variableCode")
      .containsExactly("HOLDER_NAME");
  }

  @Test
  @DisplayName("update：保存唯一下一版本草稿并持久化字体样式")
  void update_createsNextDraftWithStyles() {
    CertificateTemplate template = template(1L, 2, 3);
    when(templateMapper.selectById(1L)).thenReturn(template);
    when(templateVersionMapper.selectList(any())).thenReturn(List.of(version(11L, 2, "PUBLISHED")));
    when(templateVersionMapper.insert(any(CertificateTemplateVersion.class))).thenAnswer(invocation -> {
      CertificateTemplateVersion value = invocation.getArgument(0);
      value.setTemplateVersionId(12L);
      return 1;
    });
    CertificateTemplateUpdateRequest request = updateRequest(3);

    service.update(1L, request);

    ArgumentCaptor<CertificateTemplateVersion> versionCaptor = ArgumentCaptor.forClass(CertificateTemplateVersion.class);
    verify(templateVersionMapper).insert(versionCaptor.capture());
    assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(3);
    assertThat(versionCaptor.getValue().getPublishedStatus()).isEqualTo("DRAFT");
    ArgumentCaptor<CertificateTemplateElement> elementCaptor = ArgumentCaptor.forClass(CertificateTemplateElement.class);
    verify(templateElementMapper).insert(elementCaptor.capture());
    assertThat(elementCaptor.getValue().getFontFamily()).isEqualTo("Serif");
    assertThat(elementCaptor.getValue().getFontSize()).isEqualTo(28);
    assertThat(elementCaptor.getValue().getFontColor()).isEqualTo("#112233");
    verify(renderer).validateBackground(9L);
  }

  @Test
  @DisplayName("update：乐观锁版本不一致时拒绝保存草稿")
  void update_rejectsStaleTemplateVersion() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L, 2, 4));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.update(1L, updateRequest(3)))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 9406);
    verify(templateVersionMapper, never()).insert(any(CertificateTemplateVersion.class));
  }

  @Test
  @DisplayName("update：元素尺寸越过画布边界时拒绝保存")
  void update_rejectsElementOutsideCanvas() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L, 2, 3));
    CertificateTemplateUpdateRequest request = updateRequest(3);
    request.getElements().getFirst().setWidth(1190);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.update(1L, request))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
      .hasFieldOrPropertyWithValue("code", 9405);
    verify(templateMapper, never()).updateById(any(CertificateTemplate.class));
  }

  @Test
  @DisplayName("delete：模板被规则引用时返回冲突且不删除")
  void delete_rejectsReferencedTemplate() {
    when(templateMapper.selectById(1L)).thenReturn(template(1L, 0, 0));
    when(ruleMapper.selectCount(any())).thenReturn(1L);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.delete(1L))
      .isInstanceOf(BusinessException.class)
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 9403);
    verify(templateMapper, never()).deleteById(anyLong());
  }

  @Test
  @DisplayName("publishVersion：发布人取自登录态，不写死 0")
  void publishVersion_derivesPublishedByFromLoginState() {
    CertificateTemplate template = template(1L, 1, 3);
    when(templateMapper.selectById(1L)).thenReturn(template);
    when(templateVersionMapper.selectList(any())).thenReturn(List.of(version(10L, 1, "PUBLISHED")));
    when(templateVersionMapper.insert(any(CertificateTemplateVersion.class))).thenAnswer(inv -> {
      CertificateTemplateVersion v = inv.getArgument(0);
      v.setTemplateVersionId(11L);
      return 1;
    });
    CertificateTemplatePublishRequest req = new CertificateTemplatePublishRequest();
    req.setBackgroundFileId(9L);
    req.setWidthPx(1200);
    req.setHeightPx(900);
    req.setVersion(3);
    req.setElements(List.of());

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(77L);
      service.publishVersion(1L, req);
    }

    ArgumentCaptor<CertificateTemplateVersion> captor =
      ArgumentCaptor.forClass(CertificateTemplateVersion.class);
    verify(templateVersionMapper).insert(captor.capture());
    assertThat(captor.getValue().getPublishedBy()).isEqualTo(77L);
    assertThat(captor.getValue().getVersionNo()).isEqualTo(2);
    assertThat(captor.getValue().getPublishedStatus()).isEqualTo("PUBLISHED");
  }

  private static CertificateTemplate template(Long id, int currentVersionNo, int version) {
    CertificateTemplate template = new CertificateTemplate();
    template.setCertificateTemplateId(id);
    template.setTemplateCode("CERT-TPL");
    template.setTemplateName("培训合格证");
    template.setDescription("模板说明");
    template.setStatus(1);
    template.setCurrentVersionNo(currentVersionNo);
    template.setVersion(version);
    return template;
  }

  private static CertificateTemplateVersion version(Long id, int versionNo, String status) {
    CertificateTemplateVersion version = new CertificateTemplateVersion();
    version.setTemplateVersionId(id);
    version.setCertificateTemplateId(1L);
    version.setVersionNo(versionNo);
    version.setBackgroundFileId(9L);
    version.setWidthPx(1200);
    version.setHeightPx(900);
    version.setPublishedStatus(status);
    return version;
  }

  private static CertificateTemplateElement element(Long id, Long versionId, String type,
                                                    String variableCode, String staticContent) {
    CertificateTemplateElement element = new CertificateTemplateElement();
    element.setTemplateElementId(id);
    element.setTemplateVersionId(versionId);
    element.setElementType(type);
    element.setVariableCode(variableCode);
    element.setStaticContent(staticContent);
    element.setXPosition(10);
    element.setYPosition(20);
    element.setSortOrder(1);
    element.setStatus(1);
    return element;
  }

  private static CertificateTemplateUpdateRequest updateRequest(int version) {
    CertificateElementRequest element = new CertificateElementRequest();
    element.setElementType("VARIABLE");
    element.setVariableCode("HOLDER_NAME");
    element.setXPosition(20);
    element.setYPosition(30);
    element.setFontFamily("Serif");
    element.setFontSize(28);
    element.setFontColor("#112233");
    element.setSortOrder(1);
    element.setStatus(1);
    CertificateTemplateUpdateRequest request = new CertificateTemplateUpdateRequest();
    request.setVersion(version);
    request.setTemplateName("培训合格证");
    request.setStatus(1);
    request.setDescription("模板说明");
    request.setBackgroundFileId(9L);
    request.setWidthPx(1200);
    request.setHeightPx(900);
    request.setElements(List.of(element));
    return request;
  }
}

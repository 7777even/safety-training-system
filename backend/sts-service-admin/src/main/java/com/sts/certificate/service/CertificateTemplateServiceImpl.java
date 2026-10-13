package com.sts.certificate.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sts.admin.storage.GeneratedFileStore;
import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.dto.CertificateElementRequest;
import com.sts.certificate.dto.CertificateTemplateCreateRequest;
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
import com.sts.certificate.vo.CertificateTemplateElementVO;
import com.sts.certificate.vo.CertificateTemplateVO;
import com.sts.certificate.vo.CertificateTemplateVersionVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificateTemplateServiceImpl extends BaseServiceImpl<CertificateTemplateMapper, CertificateTemplate>
  implements CertificateTemplateService {

  private final CertificateTemplateMapper templateMapper;
  private final CertificateTemplateVersionMapper templateVersionMapper;
  private final CertificateTemplateElementMapper templateElementMapper;
  private final CertificateIssueRuleMapper ruleMapper;
  private final CertificateImageRenderer imageRenderer;
  private final GeneratedFileStore generatedFileStore;

  private static final Set<String> ELEMENT_TYPES = Set.of("TEXT", "VARIABLE");
  private static final Set<String> VARIABLE_CODES = Set.of("CERT_NO", "HOLDER_NAME", "SOURCE_TYPE", "ISSUED_TIME");
  /** 底图缺失或对象已不存在时统一使用 9404，与底图不可解码保持同一错误码。 */
  private static final int BACKGROUND_NOT_FOUND_CODE = 9404;

  @Override
  public PageResult<CertificateTemplateVO> page(PageRequest<Object> request) {
    var page = templateMapper.selectPage(
      new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(request.getPageNum(), request.getPageSize()),
      new LambdaQueryWrapper<CertificateTemplate>().orderByDesc(CertificateTemplate::getCreateTime));
    return new PageResult<>(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
  }

  @Override
  public CertificateTemplateVO get(Long certificateTemplateId) {
    CertificateTemplate template = requireTemplate(certificateTemplateId);
    List<CertificateTemplateVersion> versions = versions(certificateTemplateId);
    Map<Long, List<CertificateTemplateElement>> elements = elementsByVersion(versions);
    CertificateTemplateVO vo = toVO(template);
    versions.stream()
      .filter(item -> "PUBLISHED".equals(item.getPublishedStatus())
        && Objects.equals(item.getVersionNo(), template.getCurrentVersionNo()))
      .findFirst().map(item -> toVersionVO(item, elements.get(item.getTemplateVersionId())))
      .ifPresent(vo::setCurrentPublishedVersion);
    versions.stream().filter(item -> "DRAFT".equals(item.getPublishedStatus())).findFirst()
      .map(item -> toVersionVO(item, elements.get(item.getTemplateVersionId())))
      .ifPresent(vo::setDraftVersion);
    return vo;
  }

  @Override
  @Transactional
  public Long create(CertificateTemplateCreateRequest request) {
    CertificateTemplate template = new CertificateTemplate();
    template.setTemplateCode(request.getTemplateCode().trim());
    template.setTemplateName(request.getTemplateName().trim());
    template.setCurrentVersionNo(0);
    template.setStatus(request.getStatus());
    template.setDescription(request.getDescription());
    try {
      templateMapper.insert(template);
    } catch (DuplicateKeyException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, 9402, "同机构下模板编码已存在");
    }
    return template.getCertificateTemplateId();
  }

  @Override
  @Transactional
  public void update(Long certificateTemplateId, CertificateTemplateUpdateRequest request) {
    CertificateTemplate template = requireTemplate(certificateTemplateId);
    assertVersion(template, request.getVersion());
    validateDesign(request.getBackgroundFileId(), request.getWidthPx(), request.getHeightPx(), request.getElements());
    template.setTemplateName(request.getTemplateName().trim());
    template.setStatus(request.getStatus());
    template.setDescription(request.getDescription());
    if (templateMapper.updateById(template) == 0) {
      throw optimisticConflict();
    }
    saveDraft(template, request.getBackgroundFileId(), request.getWidthPx(), request.getHeightPx(),
              request.getVersionNote(), request.getElements(), versions(certificateTemplateId));
  }

  @Override
  @Transactional
  public void delete(Long certificateTemplateId) {
    requireTemplate(certificateTemplateId);
    long references = ruleMapper.selectCount(new LambdaQueryWrapper<CertificateIssueRule>()
                                               .eq(CertificateIssueRule::getCertificateTemplateId,
                                                   certificateTemplateId));
    if (references > 0) {
      throw new BusinessException(HttpStatus.CONFLICT, 9403, "证书模板仍被签发规则使用");
    }
    this.removeById(certificateTemplateId);
  }

  @Override
  @Transactional
  public void publishVersion(Long certificateTemplateId, CertificateTemplatePublishRequest request) {
    CertificateTemplate template = requireTemplate(certificateTemplateId);
    assertVersion(template, request.getVersion());
    validateDesign(request.getBackgroundFileId(), request.getWidthPx(), request.getHeightPx(), request.getElements());
    List<CertificateTemplateVersion> versions = versions(certificateTemplateId);
    CertificateTemplateVersion draft = saveDraft(template, request.getBackgroundFileId(), request.getWidthPx(),
                                                 request.getHeightPx(), request.getVersionNote(),
                                                 request.getElements(), versions);
    versions.stream()
      .filter(item -> "PUBLISHED".equals(item.getPublishedStatus())
        && Objects.equals(item.getVersionNo(), template.getCurrentVersionNo()))
      .forEach(item -> {
        item.setPublishedStatus("ARCHIVED");
        templateVersionMapper.updateById(item);
      });
    draft.setPublishedStatus("PUBLISHED");
    draft.setPublishedBy(StpUtil.getLoginIdAsLong());
    draft.setPublishedTime(LocalDateTime.now());
    templateVersionMapper.updateById(draft);
    template.setCurrentVersionNo(draft.getVersionNo());
    if (templateMapper.updateById(template) == 0) {
      throw optimisticConflict();
    }
  }

  @Override
  public StoredDownload openBackground(Long certificateTemplateId) {
    requireTemplate(certificateTemplateId);
    List<CertificateTemplateVersion> versions = versions(certificateTemplateId);
    CertificateTemplateVersion draft = versions.stream()
      .filter(item -> "DRAFT".equals(item.getPublishedStatus()))
      .findFirst().orElse(null);
    CertificateTemplateVersion published = versions.stream()
      .filter(item -> "PUBLISHED".equals(item.getPublishedStatus()))
      .findFirst().orElse(null);
    CertificateTemplateVersion designed = draft != null ? draft : published;
    if (designed == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, BACKGROUND_NOT_FOUND_CODE, "证书模板尚未设计底图");
    }
    return generatedFileStore.open(designed.getBackgroundFileId(), BACKGROUND_NOT_FOUND_CODE,
                                   "证书模板背景图片不存在");
  }

  private CertificateTemplate requireTemplate(Long id) {
    CertificateTemplate template = templateMapper.selectById(id);
    if (template == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 9401, "证书模板不存在");
    }
    return template;
  }

  private CertificateTemplateVO toVO(CertificateTemplate template) {
    CertificateTemplateVO vo = new CertificateTemplateVO();
    BeanUtils.copyProperties(template, vo);
    return vo;
  }

  private List<CertificateTemplateVersion> versions(Long templateId) {
    return templateVersionMapper.selectList(new LambdaQueryWrapper<CertificateTemplateVersion>()
                                              .eq(CertificateTemplateVersion::getCertificateTemplateId, templateId)
                                              .orderByDesc(CertificateTemplateVersion::getVersionNo));
  }

  private Map<Long, List<CertificateTemplateElement>> elementsByVersion(List<CertificateTemplateVersion> versions) {
    List<Long> ids = versions.stream().map(CertificateTemplateVersion::getTemplateVersionId)
      .filter(Objects::nonNull).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return templateElementMapper.selectList(new LambdaQueryWrapper<CertificateTemplateElement>()
                                              .in(CertificateTemplateElement::getTemplateVersionId, ids)
                                              .orderByAsc(CertificateTemplateElement::getSortOrder)
                                              .orderByAsc(CertificateTemplateElement::getTemplateElementId))
      .stream().collect(Collectors.groupingBy(CertificateTemplateElement::getTemplateVersionId));
  }

  private CertificateTemplateVersion saveDraft(CertificateTemplate template, Long backgroundFileId,
                                               Integer widthPx, Integer heightPx, String versionNote,
                                               List<CertificateElementRequest> requests,
                                               List<CertificateTemplateVersion> versions) {
    CertificateTemplateVersion draft = versions.stream()
      .filter(item -> "DRAFT".equals(item.getPublishedStatus())).findFirst().orElse(null);
    if (draft == null) {
      draft = new CertificateTemplateVersion();
      draft.setCertificateTemplateId(template.getCertificateTemplateId());
      draft.setVersionNo((template.getCurrentVersionNo() == null ? 0 : template.getCurrentVersionNo()) + 1);
      draft.setPublishedStatus("DRAFT");
      applyDesign(draft, backgroundFileId, widthPx, heightPx, versionNote);
      templateVersionMapper.insert(draft);
    } else {
      applyDesign(draft, backgroundFileId, widthPx, heightPx, versionNote);
      templateVersionMapper.updateById(draft);
      templateElementMapper.update(null, new LambdaUpdateWrapper<CertificateTemplateElement>()
        .setSql("deleted = template_element_id")
        .eq(CertificateTemplateElement::getTemplateVersionId, draft.getTemplateVersionId()));
    }
    for (CertificateElementRequest request : requests) {
      CertificateTemplateElement element = new CertificateTemplateElement();
      element.setTemplateVersionId(draft.getTemplateVersionId());
      element.setElementType(request.getElementType());
      element.setVariableCode(request.getVariableCode());
      element.setStaticContent(request.getStaticContent());
      element.setXPosition(request.getXPosition());
      element.setYPosition(request.getYPosition());
      element.setWidth(request.getWidth());
      element.setHeight(request.getHeight());
      element.setFontFamily(request.getFontFamily());
      element.setFontSize(request.getFontSize());
      element.setFontColor(request.getFontColor());
      element.setSortOrder(request.getSortOrder());
      element.setStatus(request.getStatus());
      templateElementMapper.insert(element);
    }
    return draft;
  }

  private static void applyDesign(CertificateTemplateVersion draft, Long backgroundFileId,
                                  Integer widthPx, Integer heightPx, String versionNote) {
    draft.setBackgroundFileId(backgroundFileId);
    draft.setWidthPx(widthPx);
    draft.setHeightPx(heightPx);
    draft.setVersionNote(versionNote);
  }

  private void validateDesign(Long backgroundFileId, Integer widthPx, Integer heightPx,
                              List<CertificateElementRequest> elements) {
    if (widthPx == null || widthPx <= 0 || heightPx == null || heightPx <= 0 || elements == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9405, "证书模板设计参数不合法");
    }
    imageRenderer.validateBackground(backgroundFileId);
    for (CertificateElementRequest element : elements) {
      validateElement(element, widthPx, heightPx);
    }
  }

  private static void validateElement(CertificateElementRequest element, int widthPx, int heightPx) {
    if (element == null || !ELEMENT_TYPES.contains(element.getElementType())
      || element.getXPosition() == null || element.getXPosition() < 0 || element.getXPosition() >= widthPx
      || element.getYPosition() == null || element.getYPosition() < 0 || element.getYPosition() >= heightPx
      || (element.getWidth() != null && (element.getWidth() <= 0
      || (long) element.getXPosition() + element.getWidth() > widthPx))
      || (element.getHeight() != null && (element.getHeight() <= 0
      || (long) element.getYPosition() + element.getHeight() > heightPx))
      || element.getSortOrder() == null || element.getSortOrder() < 0
      || (element.getFontSize() != null && element.getFontSize() <= 0)
      || (StringUtils.hasText(element.getFontColor()) && !element.getFontColor().matches("#[0-9A-Fa-f]{6}"))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9405, "证书模板元素不合法");
    }
    if ("TEXT".equals(element.getElementType()) && !StringUtils.hasText(element.getStaticContent())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9405, "文字元素内容不能为空");
    }
    if ("VARIABLE".equals(element.getElementType()) && !VARIABLE_CODES.contains(element.getVariableCode())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 9405, "证书模板变量不受支持");
    }
  }

  private static void assertVersion(CertificateTemplate template, Integer requestedVersion) {
    if (!Objects.equals(template.getVersion(), requestedVersion)) {
      throw optimisticConflict();
    }
  }

  private static BusinessException optimisticConflict() {
    return new BusinessException(HttpStatus.CONFLICT, 9406, "证书模板已被其他操作修改");
  }

  private CertificateTemplateVersionVO toVersionVO(CertificateTemplateVersion version,
                                                   List<CertificateTemplateElement> elements) {
    CertificateTemplateVersionVO vo = new CertificateTemplateVersionVO();
    BeanUtils.copyProperties(version, vo);
    vo.setElements((elements == null ? List.<CertificateTemplateElement>of() : elements).stream()
                     .sorted(Comparator.comparing(CertificateTemplateElement::getSortOrder)
                               .thenComparing(CertificateTemplateElement::getTemplateElementId))
                     .map(this::toElementVO).toList());
    return vo;
  }

  private CertificateTemplateElementVO toElementVO(CertificateTemplateElement element) {
    CertificateTemplateElementVO vo = new CertificateTemplateElementVO();
    BeanUtils.copyProperties(element, vo);
    return vo;
  }
}

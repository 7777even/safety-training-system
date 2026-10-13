package com.sts.certificate.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.dto.*;
import com.sts.certificate.service.CertificateIssueRuleService;
import com.sts.certificate.service.CertificateService;
import com.sts.certificate.service.CertificateTemplateService;
import com.sts.certificate.vo.CertificateIssueRuleVO;
import com.sts.certificate.vo.CertificateRuleSourceCandidateVO;
import com.sts.certificate.vo.CertificateTemplateVO;
import com.sts.certificate.vo.CertificateVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 证书模板、签发规则、预览、已签发证书查询与本人查看下载。撤销、重发和任意手工签发不再对外提供。
 */
@RestController
@RequiredArgsConstructor
public class CertificateController {

  private final CertificateTemplateService certificateTemplateService;
  private final CertificateIssueRuleService certificateIssueRuleService;
  private final CertificateService certificateService;

  /**
   * 分页查询模板。
   */
  @GetMapping("/certificate/templates")
  @SaCheckPermission("certificate:template:list")
  public PageResult<CertificateTemplateVO> pageTemplates(PageRequest<Object> request) {
    return certificateTemplateService.page(request);
  }

  /**
   * 查询模板。
   */
  @GetMapping("/certificate/templates/{certificateTemplateId}")
  @SaCheckPermission("certificate:template:list")
  public CertificateTemplateVO getTemplate(@PathVariable Long certificateTemplateId) {
    return certificateTemplateService.get(certificateTemplateId);
  }

  /**
   * 使用示例数据预览已发布模板。
   */
  @GetMapping("/certificate/templates/{certificateTemplateId}/preview")
  @SaCheckPermission("certificate:template:list")
  public ResponseEntity<Resource> previewTemplate(@PathVariable Long certificateTemplateId) {
    return toFile(certificateService.preview(certificateTemplateId), false);
  }

  /**
   * 查看模板当前设计底图，供设计器按真实底图所见即所得定位元素。
   */
  @GetMapping("/certificate/templates/{certificateTemplateId}/background")
  @SaCheckPermission("certificate:template:list")
  public ResponseEntity<Resource> templateBackground(@PathVariable Long certificateTemplateId) {
    return toFile(certificateTemplateService.openBackground(certificateTemplateId), false);
  }

  /**
   * 创建模板。
   */
  @PostMapping("/certificate/templates")
  @SaCheckPermission("certificate:template:add")
  public Long createTemplate(@RequestBody @Valid CertificateTemplateCreateRequest request) {
    return certificateTemplateService.create(request);
  }

  /**
   * 更新模板。
   */
  @PutMapping("/certificate/templates/{certificateTemplateId}")
  @SaCheckPermission("certificate:template:edit")
  public void updateTemplate(@PathVariable Long certificateTemplateId,
                             @RequestBody @Valid CertificateTemplateUpdateRequest request) {
    certificateTemplateService.update(certificateTemplateId, request);
  }

  /**
   * 删除模板。
   */
  @DeleteMapping("/certificate/templates/{certificateTemplateId}")
  @SaCheckPermission("certificate:template:delete")
  public void deleteTemplate(@PathVariable Long certificateTemplateId) {
    certificateTemplateService.delete(certificateTemplateId);
  }

  /**
   * 发布模板。
   */
  @PostMapping("/certificate/templates/{certificateTemplateId}/publish")
  @SaCheckPermission("certificate:template:publish")
  public void publishTemplate(@PathVariable Long certificateTemplateId,
                              @RequestBody @Valid CertificateTemplatePublishRequest request) {
    certificateTemplateService.publishVersion(certificateTemplateId, request);
  }

  /**
   * 查询规则列表。
   */
  @GetMapping("/certificate/rules")
  @SaCheckPermission("certificate:rule:list")
  public List<CertificateIssueRuleVO> listRules() {
    return certificateIssueRuleService.listRules();
  }

  /**
   * 查询签发规则详情。
   */
  @GetMapping("/certificate/rules/{certificateRuleId}")
  @SaCheckPermission("certificate:rule:list")
  public CertificateIssueRuleVO getRule(@PathVariable Long certificateRuleId) {
    return certificateIssueRuleService.get(certificateRuleId);
  }

  /**
   * 创建规则。
   */
  @PostMapping("/certificate/rules")
  @SaCheckPermission("certificate:rule:add")
  public Long createRule(@RequestBody @Valid CertificateIssueRuleCreateRequest request) {
    return certificateIssueRuleService.create(request);
  }

  /**
   * 更新签发规则。
   */
  @PutMapping("/certificate/rules/{certificateRuleId}")
  @SaCheckPermission("certificate:rule:edit")
  public void updateRule(@PathVariable Long certificateRuleId,
                         @RequestBody @Valid CertificateIssueRuleUpdateRequest request) {
    certificateIssueRuleService.update(certificateRuleId, request);
  }

  /**
   * 删除规则。
   */
  @DeleteMapping("/certificate/rules/{certificateRuleId}")
  @SaCheckPermission("certificate:rule:delete")
  public void deleteRule(@PathVariable Long certificateRuleId) {
    certificateIssueRuleService.delete(certificateRuleId);
  }

  /**
   * 分页查询课程、理论考试或培训计划来源候选。
   */
  @GetMapping("/certificate/rule-source-candidates")
  @SaCheckPermission("certificate:rule:list")
  public PageResult<CertificateRuleSourceCandidateVO> pageRuleSourceCandidates(
    @Valid PageRequest<CertificateRuleSourceQuery> request) {
    return certificateIssueRuleService.pageSourceCandidates(request);
  }

  /**
   * 分页查询证书。
   */
  @GetMapping("/certificate/certificates")
  @SaCheckPermission("certificate:cert:list")
  public PageResult<CertificateVO> pageCertificates(PageRequest<CertificateQuery> request) {
    return certificateService.page(request);
  }

  /**
   * 管理员在线查看或下载已签发证书文件。
   */
  @GetMapping("/certificate/certificates/{certificateId}/file")
  @SaCheckPermission("certificate:cert:list")
  public ResponseEntity<Resource> downloadCertificate(@PathVariable Long certificateId) {
    return toFile(certificateService.openCertificateFile(certificateId, StpUtil.getLoginIdAsLong(), true), true);
  }

  /**
   * 学员本人证书分页。
   */
  @GetMapping("/certificate/my-certificates")
  @SaCheckPermission(value = {"certificate:cert:list", "training:learner:task"}, mode = SaMode.OR)
  public PageResult<CertificateVO> pageMine(PageRequest<CertificateQuery> request) {
    return certificateService.pageMine(StpUtil.getLoginIdAsLong(), request);
  }

  /**
   * 学员查看或下载本人证书文件。
   */
  @GetMapping("/certificate/my-certificates/{certificateId}/file")
  @SaCheckPermission(value = {"certificate:cert:list", "training:learner:task"}, mode = SaMode.OR)
  public ResponseEntity<Resource> downloadMine(@PathVariable Long certificateId) {
    return toFile(certificateService.openCertificateFile(certificateId, StpUtil.getLoginIdAsLong(), false), true);
  }

  private static ResponseEntity<Resource> toFile(StoredDownload file, boolean attachment) {
    ContentDisposition disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
      .filename(file.fileName(), StandardCharsets.UTF_8)
      .build();
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(file.contentType()))
      .contentLength(file.fileSize())
      .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
      .body(file.resource());
  }
}

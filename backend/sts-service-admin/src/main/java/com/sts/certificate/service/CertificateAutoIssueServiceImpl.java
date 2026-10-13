package com.sts.certificate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.certificate.dto.CertificateIssueRequest;
import com.sts.certificate.entity.CertificateIssueRule;
import com.sts.certificate.entity.CertificateTemplate;
import com.sts.certificate.entity.CertificateTemplateVersion;
import com.sts.certificate.mapper.CertificateIssueRuleMapper;
import com.sts.certificate.mapper.CertificateTemplateMapper;
import com.sts.certificate.mapper.CertificateTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按来源主数据标识匹配启用且自动签发的规则，并调用证书签发。
 *
 * <p>只依赖证书域自身 Mapper 与 {@link CertificateService}，不依赖课程、考试、培训任何实体，
 * 避免业务域之间的反向依赖。重复触发的幂等由证书唯一键与服务内部前置查询共同保证。
 */
@Service
@RequiredArgsConstructor
public class CertificateAutoIssueServiceImpl implements CertificateAutoIssueService {

  private final CertificateIssueRuleMapper ruleMapper;
  private final CertificateTemplateMapper templateMapper;
  private final CertificateTemplateVersionMapper templateVersionMapper;
  private final CertificateService certificateService;

  @Override
  public void issueForSource(String sourceType, Long sourceId, Long userId, Long sourceResultId) {
    if (sourceType == null || sourceId == null || userId == null) {
      return;
    }
    List<CertificateIssueRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<CertificateIssueRule>()
                                                               .eq(CertificateIssueRule::getSourceType, sourceType)
                                                               .eq(CertificateIssueRule::getSourceId, sourceId)
                                                               .eq(CertificateIssueRule::getStatus, 1)
                                                               .eq(CertificateIssueRule::getIsAutoIssue, 1));
    for (CertificateIssueRule rule : rules) {
      issueWithRule(rule, sourceType, sourceId, userId, sourceResultId);
    }
  }

  /**
   * 单条规则的签发尝试：模板未启用或缺少当前已发布版本时跳过，不阻断同来源的其他规则。
   */
  private void issueWithRule(CertificateIssueRule rule, String sourceType, Long sourceId, Long userId,
                             Long sourceResultId) {
    CertificateTemplate template = templateMapper.selectById(rule.getCertificateTemplateId());
    if (template == null || !Integer.valueOf(1).equals(template.getStatus()) || template.getCurrentVersionNo() == null) {
      return;
    }
    CertificateTemplateVersion version = templateVersionMapper.selectOne(new LambdaQueryWrapper<CertificateTemplateVersion>()
                                                                           .eq(CertificateTemplateVersion::getCertificateTemplateId,
                                                                               template.getCertificateTemplateId())
                                                                           .eq(CertificateTemplateVersion::getVersionNo,
                                                                               template.getCurrentVersionNo())
                                                                           .eq(CertificateTemplateVersion::getPublishedStatus,
                                                                               "PUBLISHED"));
    if (version == null) {
      return;
    }
    CertificateIssueRequest request = new CertificateIssueRequest();
    request.setCertificateRuleId(rule.getCertificateRuleId());
    request.setCertificateTemplateId(template.getCertificateTemplateId());
    request.setTemplateVersionId(version.getTemplateVersionId());
    request.setHolderUserId(userId);
    request.setSourceType(sourceType);
    request.setSourceId(sourceId);
    request.setSourceResultId(sourceResultId);
    certificateService.issue(request);
  }
}

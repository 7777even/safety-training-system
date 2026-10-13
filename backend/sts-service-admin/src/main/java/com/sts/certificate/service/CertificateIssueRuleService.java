package com.sts.certificate.service;

import com.sts.certificate.dto.CertificateIssueRuleCreateRequest;
import com.sts.certificate.dto.CertificateIssueRuleUpdateRequest;
import com.sts.certificate.dto.CertificateRuleSourceQuery;
import com.sts.certificate.vo.CertificateIssueRuleVO;
import com.sts.certificate.vo.CertificateRuleSourceCandidateVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

import java.util.List;

public interface CertificateIssueRuleService {
  List<CertificateIssueRuleVO> listRules();

  CertificateIssueRuleVO get(Long certificateRuleId);
  Long create(CertificateIssueRuleCreateRequest request);

  void update(Long certificateRuleId, CertificateIssueRuleUpdateRequest request);
  void delete(Long certificateRuleId);

  PageResult<CertificateRuleSourceCandidateVO> pageSourceCandidates(
    PageRequest<CertificateRuleSourceQuery> request);
}

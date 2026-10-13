package com.sts.certificate.service;

import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.dto.CertificateIssueRequest;
import com.sts.certificate.dto.CertificateQuery;
import com.sts.certificate.vo.CertificateVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

public interface CertificateService {
  /**
   * 内部签发；不作为公开手工签发入口。
   */
  CertificateVO issue(CertificateIssueRequest request);

  PageResult<CertificateVO> page(PageRequest<CertificateQuery> request);

  /**
   * 学员仅能查看本人证书。
   */
  PageResult<CertificateVO> pageMine(Long holderUserId, PageRequest<CertificateQuery> request);

  StoredDownload openCertificateFile(Long certificateId, Long currentUserId, boolean adminView);

  StoredDownload preview(Long certificateTemplateId);
}

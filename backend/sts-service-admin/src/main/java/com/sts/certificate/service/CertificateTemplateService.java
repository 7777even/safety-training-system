package com.sts.certificate.service;

import com.sts.admin.storage.StoredDownload;
import com.sts.certificate.dto.CertificateTemplateCreateRequest;
import com.sts.certificate.dto.CertificateTemplatePublishRequest;
import com.sts.certificate.dto.CertificateTemplateUpdateRequest;
import com.sts.certificate.vo.CertificateTemplateVO;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;

public interface CertificateTemplateService {
  PageResult<CertificateTemplateVO> page(PageRequest<Object> request);
  CertificateTemplateVO get(Long certificateTemplateId);
  Long create(CertificateTemplateCreateRequest request);
  void update(Long certificateTemplateId, CertificateTemplateUpdateRequest request);
  void delete(Long certificateTemplateId);
  void publishVersion(Long certificateTemplateId, CertificateTemplatePublishRequest request);
  /** 读取模板当前设计所用底图，供设计器按真实底图所见即所得定位元素。 */
  StoredDownload openBackground(Long certificateTemplateId);
}

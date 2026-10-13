package com.sts.certificate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CertificateTemplatePublishRequest {
  /**
   * 模板乐观锁版本号。
   */
  @NotNull
  private Integer version;
  @NotNull
  private Long backgroundFileId;
  @NotNull
  private Integer widthPx;
  @NotNull
  private Integer heightPx;
  private String versionNote;
  @Valid
  @NotNull
  private List<CertificateElementRequest> elements;
}

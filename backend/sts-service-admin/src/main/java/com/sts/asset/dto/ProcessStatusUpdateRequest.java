package com.sts.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 转码状态回写请求。本次不接入真实转码供应商回调，供内部/后续对接方以同一契约推进状态。
 */
@Data
public class ProcessStatusUpdateRequest {
  @NotBlank(message = "处理状态不能为空")
  @Pattern(regexp = "UPLOADED|PROCESSING|SUCCEEDED|FAILED", message = "处理状态仅支持 UPLOADED、PROCESSING、SUCCEEDED 或 FAILED")
  private String processStatus;
}

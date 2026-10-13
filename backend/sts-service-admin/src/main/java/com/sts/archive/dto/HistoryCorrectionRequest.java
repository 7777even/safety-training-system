package com.sts.archive.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Map;

/** 仅允许摘要字段，禁止携带答卷、冻结结果或证书内容。 */
@Data
public class HistoryCorrectionRequest {
  /** 当前档案展示版本，用于并发冲突检测。 */
  @NotNull @Min(0) private Integer version;
  /** 要更正的字段及后值，服务端按对应来源校验类型。 */
  @NotEmpty @Size(max = 6) private Map<String, JsonNode> changes;
  /** 更正依据，不能留空。 */
  @NotBlank @Size(max = 500) private String reason;
}

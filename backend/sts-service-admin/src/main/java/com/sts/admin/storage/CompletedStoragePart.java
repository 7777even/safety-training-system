package com.sts.admin.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CompletedStoragePart(
  @Min(value = 1, message = "分片号必须大于 0") int partNumber,
  @NotBlank(message = "分片 ETag 不能为空") String etag) {
}

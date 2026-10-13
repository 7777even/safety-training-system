package com.sts.admin.system.dto;

import com.sts.admin.storage.CompletedStoragePart;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MultipartCompleteRequest(
  @NotEmpty(message = "完成上传至少需要一个分片") List<@Valid CompletedStoragePart> parts) {
}

package com.sts.admin.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MultipartInitRequest {
  @NotBlank(message = "文件名不能为空")
  private String fileName;
  @NotBlank(message = "Content-Type 不能为空")
  private String contentType;
  @NotNull(message = "文件大小不能为空")
  @Positive(message = "文件大小必须大于 0")
  private Long fileSize;
  @NotBlank(message = "checksum 不能为空")
  @Pattern(regexp = "^[a-f0-9]{64}$", message = "checksum 必须是 SHA-256 小写十六进制")
  private String checksum;
  @NotBlank(message = "业务目录不能为空")
  @Pattern(regexp = "^[a-zA-Z0-9_-]{1,64}$", message = "业务目录格式不正确")
  private String folder;
}

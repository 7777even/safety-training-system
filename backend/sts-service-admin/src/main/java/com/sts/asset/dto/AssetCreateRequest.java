package com.sts.asset.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssetCreateRequest {
  @NotBlank(message = "素材编码不能为空")
  private String assetCode;

  @NotBlank(message = "素材名称不能为空")
  private String assetName;

  @NotBlank(message = "素材类型不能为空")
  private String assetType;

  @NotBlank(message = "来源类型不能为空")
  private String sourceType;

  /**
   * UPLOAD 来源必填：对应 us_sys_file_info 已上传的文件。
   */
  private Long fileInfoId;

  /**
   * EXTERNAL 来源必填。
   */
  private String externalUrl;

  /**
   * CREATED 来源必填（富文本内容）。
   */
  private String richContent;

  private String mimeType;
  private Long fileSize;
  private Integer pageCount;
  private Integer durationSeconds;
  private String checksum;
  private Integer status = 1;
}

package com.sts.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sts.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sts_file_multipart_upload")
public class FileMultipartUpload extends BaseEntity {
  @TableId(type = IdType.AUTO)
  private Long multipartUploadId;
  private String uploadId;
  private Long organizationId;
  private Long userId;
  private String providerUploadId;
  private String bucketName;
  private String objectName;
  private String folder;
  private String originalFileName;
  private String contentType;
  private Long fileSize;
  private String checksum;
  private Long partSize;
  private String uploadStatus;
  private Long fileInfoId;
  private LocalDateTime expiresTime;
  private LocalDateTime completedTime;
}

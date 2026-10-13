package com.sts.course.vo;

import lombok.Data;

/**
 * 课程封面上传结果，不暴露私有对象存储信息。
 */
@Data
public class CourseCoverUploadVO {
  private Long fileInfoId;
  private String fileName;
  private Long fileSize;
  private String contentType;
}

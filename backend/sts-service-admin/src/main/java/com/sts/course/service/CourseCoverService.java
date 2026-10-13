package com.sts.course.service;

import com.sts.course.vo.CourseCoverUploadVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 课程封面上传、引用校验和受控读取服务。
 */
public interface CourseCoverService {

  CourseCoverUploadVO upload(MultipartFile file);

  void validateReference(Long fileInfoId);

  CourseCoverFile open(Long courseId);

  record CourseCoverFile(Resource resource, String fileName, String contentType, long fileSize, String etag) {
  }
}

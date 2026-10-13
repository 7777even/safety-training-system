package com.sts.course.service;

import com.sts.course.dto.DownloadAssetReplaceRequest;
import com.sts.course.vo.DownloadAssetVO;

import java.util.List;

public interface CourseDownloadAssetService {

  List<DownloadAssetVO> listByCourse(Long courseId);

  void replaceAll(Long courseId, DownloadAssetReplaceRequest request);

  void deleteAllOfCourse(Long courseId);
}

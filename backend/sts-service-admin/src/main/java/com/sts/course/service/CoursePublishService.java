package com.sts.course.service;

import com.sts.course.dto.CourseOfflineRequest;
import com.sts.course.dto.CoursePublishRequest;
import com.sts.course.vo.CourseVersionListItemVO;
import com.sts.course.vo.CourseVersionVO;

import java.util.List;

public interface CoursePublishService {

  Long publish(Long courseId, CoursePublishRequest request);

  void offline(Long courseId, CourseOfflineRequest request);

  List<CourseVersionListItemVO> listVersions(Long courseId);

  CourseVersionVO getVersion(Long courseId, Long versionId);
}

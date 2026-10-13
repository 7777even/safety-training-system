package com.sts.course.service;

import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.course.dto.*;
import com.sts.course.vo.CourseListItemVO;
import com.sts.course.vo.CourseVO;

public interface CourseService {

  PageResult<CourseListItemVO> pageCourses(PageRequest<CourseListQuery> request);

  CourseVO getCourse(Long courseId);

  Long createCourse(CourseCreateRequest request);

  void updateCourse(Long courseId, CourseUpdateRequest request);

  void deleteCourse(Long courseId);

  void switchContentMode(Long courseId, ContentModeSwitchRequest request);

  void assignCategories(Long courseId, CategoryAssignRequest request);
}

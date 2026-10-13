package com.sts.course.support;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.course.dto.CoursePublishRequest;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.service.CoursePublishService;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/**
 * 课程发布集成测试共享种子与发布流程（mockStatic 隔离 Sa-Token）。
 */
public final class CoursePublishItHelper {

  private CoursePublishItHelper() {
  }

  public static Long seedCourseWithChapter(Long organizationId,
                                           CourseMapper courseMapper,
                                           CourseChapterMapper chapterMapper,
                                           String codePrefix) {
    Course course = CourseItFixtures.newCourse(organizationId,
                                               CourseItFixtures.uniqueCourseCode(codePrefix));
    courseMapper.insert(course);

    var chapter = CourseItFixtures.newChapter(course.getCourseId(), "第一章");
    chapterMapper.insert(chapter);

    return course.getCourseId();
  }

  public static Long publishCourse(Long courseId,
                                   CoursePublishService publishService,
                                   CourseMapper courseMapper) {
    Course course = courseMapper.selectById(courseId);
    CoursePublishRequest request = new CoursePublishRequest();
    request.setVersion(course.getVersion());
    request.setVersionNote("IT publish");

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
      return publishService.publish(courseId, request);
    }
  }
}

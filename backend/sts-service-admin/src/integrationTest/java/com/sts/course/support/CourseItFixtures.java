package com.sts.course.support;

import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 课程域集成测试共享构造器（必填 NOT NULL 字段与 DEFAULT 机构解析）。
 */
public final class CourseItFixtures {

  private CourseItFixtures() {
  }

  public static Long defaultOrganizationId(OrganizationMapper organizationMapper) {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();
    return organization.getOrganizationId();
  }

  public static Course newCourse(Long organizationId, String courseCode) {
    Course course = new Course();
    course.setOrganizationId(organizationId);
    course.setCourseCode(courseCode);
    course.setCourseName("安全教育课程");
    course.setContentMode("MULTI_CHAPTER");
    course.setCredit(BigDecimal.ONE);
    course.setPointMode("NONE");
    course.setPointValue(BigDecimal.ZERO);
    course.setSourceDownloadMode("NONE");
    course.setIsSpeedPlayAllowed(0);
    course.setIsStudyAnticheatEnabled(0);
    course.setIsStudyDescriptionEnabled(1);
    course.setIsWatermarkEnabled(0);
    course.setPublishStatus("DRAFT");
    course.setStatus(1);
    return course;
  }

  public static CourseChapter newChapter(Long courseId, String chapterName) {
    CourseChapter chapter = new CourseChapter();
    chapter.setCourseId(courseId);
    chapter.setParentId(0L);
    chapter.setAncestors("0");
    chapter.setChapterName(chapterName);
    chapter.setSortOrder(0);
    chapter.setStatus(1);
    return chapter;
  }

  public static String uniqueCourseCode(String prefix) {
    return prefix + System.nanoTime();
  }
}

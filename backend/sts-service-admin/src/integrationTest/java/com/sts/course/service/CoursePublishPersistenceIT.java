package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseChapterMapper;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.org.mapper.OrganizationMapper;
import com.sts.course.support.CourseItFixtures;
import com.sts.course.support.CourseMapperItConfig;
import com.sts.course.support.CoursePublishItHelper;
import com.sts.course.support.CoursePublishServiceItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 课程发布真 MySQL 集成测试：首次发布写 version、幂等、内容变更后 version_no 递增。
 */
@SpringBootTest(classes = {CourseMapperItConfig.class, CoursePublishServiceItConfig.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CoursePublishPersistenceIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CoursePublishService publishService;
  @Autowired
  private CourseMapper courseMapper;
  @Autowired
  private CourseChapterMapper chapterMapper;
  @Autowired
  private CourseVersionMapper courseVersionMapper;
  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("publish：首次发布写 version_no=1，幂等不插第二行，内容变更后 version_no=2")
  void publish_firstPublish_idempotentAndIncrementsOnChange() {
    Long orgId = CourseItFixtures.defaultOrganizationId(organizationMapper);
    Long courseId = CoursePublishItHelper.seedCourseWithChapter(orgId, courseMapper, chapterMapper, "IT-PUB-");

    Long firstVersionId = CoursePublishItHelper.publishCourse(courseId, publishService, courseMapper);

    CourseVersion firstVersion = courseVersionMapper.selectById(firstVersionId);
    assertThat(firstVersion).isNotNull();
    assertThat(firstVersion.getVersionNo()).isEqualTo(1);
    assertThat(firstVersion.getPublishedBy()).isEqualTo(1L);

    Course published = courseMapper.selectById(courseId);
    assertThat(published.getCurrentVersionId()).isEqualTo(firstVersionId);
    assertThat(published.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(published.getPublishedTime()).isNotNull();

    Long secondVersionId = CoursePublishItHelper.publishCourse(courseId, publishService, courseMapper);
    assertThat(secondVersionId).isEqualTo(firstVersionId);
    assertThat(countVersions(courseId)).isEqualTo(1);

    Course toUpdate = courseMapper.selectById(courseId);
    toUpdate.setCourseName("更新后的课程名");
    courseMapper.updateById(toUpdate);

    Long thirdVersionId = CoursePublishItHelper.publishCourse(courseId, publishService, courseMapper);
    assertThat(thirdVersionId).isNotEqualTo(firstVersionId);
    assertThat(countVersions(courseId)).isEqualTo(2);

    CourseVersion secondVersion = courseVersionMapper.selectById(thirdVersionId);
    assertThat(secondVersion.getVersionNo()).isEqualTo(2);

    Course afterSecondPublish = courseMapper.selectById(courseId);
    assertThat(afterSecondPublish.getCurrentVersionId()).isEqualTo(thirdVersionId);
    assertThat(afterSecondPublish.getPublishStatus()).isEqualTo("PUBLISHED");
  }

  private long countVersions(Long courseId) {
    Long count = courseVersionMapper.selectCount(
      new LambdaQueryWrapper<CourseVersion>().eq(CourseVersion::getCourseId, courseId));
    return count == null ? 0L : count;
  }
}

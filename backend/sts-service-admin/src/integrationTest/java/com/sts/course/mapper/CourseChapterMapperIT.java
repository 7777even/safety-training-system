package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseChapter;
import com.sts.org.entity.Organization;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseChapterMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_course_parent_name_deleted} 唯一索引：同课程同层（根级）同名章节只能有一条在线记录；
 * 逻辑删除后可重新插入同名章节。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseChapterMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private CourseChapterMapper chapterMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("祖先路径可超过旧1000字符限制且教师字段可清空")
  void deepAncestorsAndNullableTeacherRoundTrip() {
    Long courseId = createCourse("SAFETY-CH-B6");
    CourseChapter deep = chapter(courseId, "深层目录");
    deep.setParentId(123L); deep.setAncestors("0," + "123456789,".repeat(200));
    chapterMapper.insert(deep);
    assertThat(chapterMapper.selectById(deep.getChapterId()).getAncestors()).hasSizeGreaterThan(1000);
    assertThat(chapterMapper.selectCurrent(deep.getChapterId()).getParentId()).isEqualTo(123L);
    var course = courseMapper.selectById(courseId); course.setTeacherUserId(99L); courseMapper.updateById(course);
    assertThat(courseMapper.selectById(courseId).getTeacherUserId()).isEqualTo(99L);
    course.setTeacherUserId(null); courseMapper.updateById(course);
    assertThat(courseMapper.selectById(courseId).getTeacherUserId()).isNull();
  }

  @Test
  @DisplayName("uk_course_parent_name_deleted：同课程同名章节第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateChapterName() {
    Long courseId = createCourse("SAFETY-CH-001");

    chapterMapper.insert(chapter(courseId, "第一章"));

    assertThatThrownBy(() -> chapterMapper.insert(chapter(courseId, "第一章")))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除章节后同名可重新插入")
  void logicalDelete_allowsReinsertSameChapterName() {
    Long courseId = createCourse("SAFETY-CH-002");

    CourseChapter original = chapter(courseId, "第二章");
    chapterMapper.insert(original);

    int deletedRows = chapterMapper.update(null,
                                           Wrappers.<CourseChapter>lambdaUpdate()
                                             .setSql("deleted = chapter_id")
                                             .eq(CourseChapter::getChapterId, original.getChapterId()));
    assertThat(deletedRows).isEqualTo(1);

    CourseChapter reinserted = chapter(courseId, "第二章");
    chapterMapper.insert(reinserted);

    CourseChapter found = chapterMapper.selectOne(
      new LambdaQueryWrapper<CourseChapter>()
        .eq(CourseChapter::getCourseId, courseId)
        .eq(CourseChapter::getChapterName, "第二章")
        .eq(CourseChapter::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getChapterId()).isEqualTo(reinserted.getChapterId());
  }

  private Long createCourse(String code) {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();

    Course course = new Course();
    course.setOrganizationId(organization.getOrganizationId());
    course.setCourseCode(code);
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
    courseMapper.insert(course);
    return course.getCourseId();
  }

  private static CourseChapter chapter(Long courseId, String name) {
    CourseChapter chapter = new CourseChapter();
    chapter.setCourseId(courseId);
    chapter.setParentId(0L);
    chapter.setAncestors("0");
    chapter.setChapterName(name);
    chapter.setSortOrder(0);
    chapter.setStatus(1);
    return chapter;
  }
}

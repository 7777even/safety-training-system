package com.sts.course.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sts.admin.support.AbstractMysqlIntegrationTest;
import com.sts.course.entity.*;
import com.sts.course.support.CourseMapperItConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sts.org.entity.Organization;
import com.sts.org.mapper.OrganizationMapper;

/**
 * {@link CourseLiveSessionMapper} 集成测试（Testcontainers 真 MySQL）。
 * <p>
 * 重点验证 {@code uk_course_item_deleted(course_item_id, deleted)}：每内容项仅一条在线直播场次；
 * 逻辑删除后可重新插入。
 */
@SpringBootTest(classes = CourseMapperItConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CourseLiveSessionMapperIT extends AbstractMysqlIntegrationTest {

  @Autowired
  private CourseLiveSessionMapper liveSessionMapper;

  @Autowired
  private CourseMapper courseMapper;

  @Autowired
  private CourseChapterMapper chapterMapper;

  @Autowired
  private CourseItemMapper itemMapper;

  @Autowired
  private OrganizationMapper organizationMapper;

  @Test
  @DisplayName("uk_course_item_deleted：同内容项第二次插入触发唯一键冲突")
  void uniqueKey_rejectsDuplicateCourseItem() {
    Long courseItemId = createLiveCourseItem("SAFETY-LIVE-001");

    liveSessionMapper.insert(liveSession(courseItemId));

    assertThatThrownBy(() -> liveSessionMapper.insert(liveSession(courseItemId)))
      .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("逻辑删除后 deleted=主键ID，同内容项可重新插入")
  void logicalDelete_allowsReinsertSameCourseItem() {
    Long courseItemId = createLiveCourseItem("SAFETY-LIVE-002");

    CourseLiveSession original = liveSession(courseItemId);
    liveSessionMapper.insert(original);

    int deletedRows = liveSessionMapper.update(null,
                                               Wrappers.<CourseLiveSession>lambdaUpdate()
                                                 .setSql("deleted = live_session_id")
                                                 .eq(CourseLiveSession::getLiveSessionId, original.getLiveSessionId()));
    assertThat(deletedRows).isEqualTo(1);

    CourseLiveSession reinserted = liveSession(courseItemId);
    liveSessionMapper.insert(reinserted);

    CourseLiveSession found = liveSessionMapper.selectOne(
      new LambdaQueryWrapper<CourseLiveSession>()
        .eq(CourseLiveSession::getCourseItemId, courseItemId)
        .eq(CourseLiveSession::getDeleted, 0L));
    assertThat(found).isNotNull();
    assertThat(found.getLiveSessionId()).isEqualTo(reinserted.getLiveSessionId());
  }

  private Long createLiveCourseItem(String courseCode) {
    Long orgId = defaultOrganizationId();
    Long courseId = createCourse(orgId, courseCode);
    Long chapterId = createChapter(courseId);
    CourseItem item = new CourseItem();
    item.setCourseId(courseId);
    item.setChapterId(chapterId);
    item.setItemType("LIVE");
    item.setItemTitle("直播课");
    item.setIsRequired(1);
    item.setMinimumStudySeconds(0);
    item.setSortOrder(0);
    item.setIsLockedAfterParticipation(0);
    item.setStatus(1);
    itemMapper.insert(item);
    return item.getCourseItemId();
  }

  private Long defaultOrganizationId() {
    Organization organization = organizationMapper.selectByOrgCode("DEMO");
    assertThat(organization).isNotNull();
    return organization.getOrganizationId();
  }

  private Long createCourse(Long orgId, String code) {
    Course course = new Course();
    course.setOrganizationId(orgId);
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

  private Long createChapter(Long courseId) {
    CourseChapter chapter = new CourseChapter();
    chapter.setCourseId(courseId);
    chapter.setParentId(0L);
    chapter.setAncestors("0");
    chapter.setChapterName("第一章");
    chapter.setSortOrder(0);
    chapter.setStatus(1);
    chapterMapper.insert(chapter);
    return chapter.getChapterId();
  }

  private CourseLiveSession liveSession(Long courseItemId) {
    CourseLiveSession session = new CourseLiveSession();
    session.setOrganizationId(defaultOrganizationId());
    session.setCourseItemId(courseItemId);
    session.setLiveName("安全直播");
    session.setTeacherUserId(1L);
    session.setLiveUrl("https://live.example.com/room/1");
    session.setStartTime(LocalDateTime.now().plusDays(1));
    session.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
    session.setLiveStatus("SCHEDULED");
    session.setStatus(1);
    return session;
  }
}

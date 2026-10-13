package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseOfflineRequest;
import com.sts.course.dto.CoursePublishRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.admin.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoursePublishServiceImplTest {

  private static final Long ORG_ID = 1L;
  private static final Long COURSE_ID = 10L;
  private static final Long USER_ID = 99L;
  private static final String SNAPSHOT_JSON = "{\"schemaVersion\":1}";
  private static final String SNAPSHOT_HASH = "abc123";

  private CourseMapper courseMapper;
  private CourseVersionMapper courseVersionMapper;
  private CourseSnapshotAssembler snapshotAssembler;
  private CoursePublishServiceImpl service;

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(ORG_ID_FOR_TENANT);
    courseMapper = mock(CourseMapper.class);
    courseVersionMapper = mock(CourseVersionMapper.class);
    snapshotAssembler = mock(CourseSnapshotAssembler.class);
    service = new CoursePublishServiceImpl(courseMapper, courseVersionMapper, snapshotAssembler);
  }

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static final Long ORG_ID_FOR_TENANT = 1L;

  @Test
  @DisplayName("publish：首次发布插入 version_no=1 并更新课程为 PUBLISHED")
  void publish_firstPublish_insertsVersionAndUpdatesCourse() {
    Course course = course(COURSE_ID, null, 0);
    when(courseMapper.selectOne(any())).thenReturn(course);
    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    when(snapshotAssembler.assembleAndValidate(course)).thenReturn(snapshot);
    when(snapshotAssembler.toCompactJson(snapshot)).thenReturn(SNAPSHOT_JSON);
    when(snapshotAssembler.computeSnapshotHash(snapshot)).thenReturn(SNAPSHOT_HASH);
    when(courseVersionMapper.selectOne(any())).thenReturn(null);
    doAnswer(invocation -> {
      CourseVersion version = invocation.getArgument(0);
      version.setCourseVersionId(200L);
      return 1;
    }).when(courseVersionMapper).insert(any(CourseVersion.class));

    CoursePublishRequest request = new CoursePublishRequest();
    request.setVersion(0);
    request.setVersionNote("首次发布");

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      Long versionId = service.publish(COURSE_ID, request);
      assertThat(versionId).isNotNull();
    }

    ArgumentCaptor<CourseVersion> versionCaptor = ArgumentCaptor.forClass(CourseVersion.class);
    verify(courseVersionMapper).insert(versionCaptor.capture());
    CourseVersion inserted = versionCaptor.getValue();
    assertThat(inserted.getVersionNo()).isEqualTo(1);
    assertThat(inserted.getSnapshotHash()).isEqualTo(SNAPSHOT_HASH);
    assertThat(inserted.getPublishedBy()).isEqualTo(USER_ID);
    assertThat(inserted.getVersionNote()).isEqualTo("首次发布");

    ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
    verify(courseMapper).updateByIdWithVersionCheck(courseCaptor.capture());
    Course updated = courseCaptor.getValue();
    assertThat(updated.getPublishStatus()).isEqualTo("PUBLISHED");
    assertThat(updated.getCurrentVersionId()).isEqualTo(200L);
    assertThat(updated.getPublishedTime()).isNotNull();
  }

  @Test
  @DisplayName("publish：相同快照哈希幂等，不插入新版本")
  void publish_sameHash_isIdempotent() {
    Course course = course(COURSE_ID, 100L, 2);
    when(courseMapper.selectOne(any())).thenReturn(course);
    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    when(snapshotAssembler.assembleAndValidate(course)).thenReturn(snapshot);
    when(snapshotAssembler.toCompactJson(snapshot)).thenReturn(SNAPSHOT_JSON);
    when(snapshotAssembler.computeSnapshotHash(snapshot)).thenReturn(SNAPSHOT_HASH);

    CourseVersion current = new CourseVersion();
    current.setCourseVersionId(100L);
    current.setSnapshotHash(SNAPSHOT_HASH);
    when(courseVersionMapper.selectById(100L)).thenReturn(current);

    CoursePublishRequest request = new CoursePublishRequest();
    request.setVersion(2);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      Long versionId = service.publish(COURSE_ID, request);
      assertThat(versionId).isEqualTo(100L);
    }

    verify(courseVersionMapper, never()).insert(any(CourseVersion.class));
    verify(courseMapper, never()).updateByIdWithVersionCheck(any(Course.class));
  }

  @Test
  @DisplayName("publish：内容变更后 version_no 递增")
  void publish_contentChanged_incrementsVersionNo() {
    Course course = course(COURSE_ID, 100L, 1);
    when(courseMapper.selectOne(any())).thenReturn(course);
    CourseContentSnapshot snapshot = new CourseContentSnapshot();
    when(snapshotAssembler.assembleAndValidate(course)).thenReturn(snapshot);
    when(snapshotAssembler.toCompactJson(snapshot)).thenReturn(SNAPSHOT_JSON);
    when(snapshotAssembler.computeSnapshotHash(snapshot)).thenReturn("new-hash");

    CourseVersion current = new CourseVersion();
    current.setCourseVersionId(100L);
    current.setSnapshotHash("old-hash");
    when(courseVersionMapper.selectById(100L)).thenReturn(current);

    CourseVersion latest = new CourseVersion();
    latest.setVersionNo(1);
    when(courseVersionMapper.selectOne(any())).thenReturn(latest);

    CoursePublishRequest request = new CoursePublishRequest();
    request.setVersion(1);

    try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
      stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
      service.publish(COURSE_ID, request);
    }

    ArgumentCaptor<CourseVersion> versionCaptor = ArgumentCaptor.forClass(CourseVersion.class);
    verify(courseVersionMapper).insert(versionCaptor.capture());
    assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(2);
    verify(courseMapper).updateByIdWithVersionCheck(any(Course.class));
  }

  @Test
  @DisplayName("publish：无章节 → 400 / 5051")
  void publish_noChapters_throws5051() {
    Course course = course(COURSE_ID, null, 0);
    when(courseMapper.selectOne(any())).thenReturn(course);
    when(snapshotAssembler.assembleAndValidate(course))
      .thenThrow(new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, 5051, "课程无可发布内容"));

    CoursePublishRequest request = new CoursePublishRequest();
    request.setVersion(0);

    assertThatThrownBy(() -> service.publish(COURSE_ID, request))
      .isInstanceOf(BusinessException.class)
      .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(5051));
    verify(courseVersionMapper, never()).insert(any(CourseVersion.class));
  }

  @Test
  @DisplayName("offline：将课程 publishStatus 置为 OFFLINE")
  void offline_setsPublishStatusOffline() {
    Course course = course(COURSE_ID, 100L, 3);
    when(courseMapper.selectOne(any())).thenReturn(course);

    CourseOfflineRequest request = new CourseOfflineRequest();
    request.setVersion(3);

    service.offline(COURSE_ID, request);

    ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
    verify(courseMapper).updateByIdWithVersionCheck(courseCaptor.capture());
    assertThat(courseCaptor.getValue().getPublishStatus()).isEqualTo("OFFLINE");
    assertThat(courseCaptor.getValue().getVersion()).isEqualTo(3);
  }

  private Course course(Long courseId, Long currentVersionId, int version) {
    Course course = new Course();
    course.setCourseId(courseId);
    course.setOrganizationId(ORG_ID);
    course.setCurrentVersionId(currentVersionId);
    course.setVersion(version);
    return course;
  }
}

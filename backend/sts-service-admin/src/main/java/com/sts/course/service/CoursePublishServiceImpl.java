package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.CourseOfflineRequest;
import com.sts.course.dto.CoursePublishRequest;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.vo.CourseVersionListItemVO;
import com.sts.course.vo.CourseVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CoursePublishServiceImpl implements CoursePublishService {

  private static final String STATUS_PUBLISHED = "PUBLISHED";
  private static final String STATUS_OFFLINE = "OFFLINE";

  private final CourseMapper courseMapper;
  private final CourseVersionMapper courseVersionMapper;
  private final CourseSnapshotAssembler snapshotAssembler;

  @Override
  @Transactional
  public Long publish(Long courseId, CoursePublishRequest request) {
    Course course = requireCourse(courseId);
    assertVersionMatches(course, request.getVersion());

    CourseContentSnapshot snapshot = snapshotAssembler.assembleAndValidate(course);
    String snapshotJson = snapshotAssembler.toCompactJson(snapshot);
    String snapshotHash = snapshotAssembler.computeSnapshotHash(snapshot);

    if (course.getCurrentVersionId() != null) {
      CourseVersion current = courseVersionMapper.selectById(course.getCurrentVersionId());
      if (current != null && Objects.equals(current.getSnapshotHash(), snapshotHash)) {
        return current.getCourseVersionId();
      }
    }

    int nextVersionNo = resolveNextVersionNo(courseId);
    Long publisherId = StpUtil.getLoginIdAsLong();
    LocalDateTime publishedTime = LocalDateTime.now();

    CourseVersion version = new CourseVersion();
    version.setCourseId(courseId);
    version.setVersionNo(nextVersionNo);
    version.setContentSnapshotJson(snapshotJson);
    version.setSnapshotHash(snapshotHash);
    version.setVersionNote(request.getVersionNote());
    version.setPublishedBy(publisherId);
    version.setPublishedTime(publishedTime);
    courseVersionMapper.insert(version);

    course.setCurrentVersionId(version.getCourseVersionId());
    course.setPublishStatus(STATUS_PUBLISHED);
    course.setPublishedTime(publishedTime);
    course.setVersion(request.getVersion());
    courseMapper.updateByIdWithVersionCheck(course);
    return version.getCourseVersionId();
  }

  @Override
  @Transactional
  public void offline(Long courseId, CourseOfflineRequest request) {
    Course course = requireCourse(courseId);
    course.setVersion(request.getVersion());
    course.setPublishStatus(STATUS_OFFLINE);
    courseMapper.updateByIdWithVersionCheck(course);
  }

  @Override
  public List<CourseVersionListItemVO> listVersions(Long courseId) {
    requireCourse(courseId);
    return courseVersionMapper.selectList(
        new LambdaQueryWrapper<CourseVersion>()
          .eq(CourseVersion::getCourseId, courseId)
          .orderByDesc(CourseVersion::getVersionNo))
      .stream().map(this::toListItem).toList();
  }

  @Override
  public CourseVersionVO getVersion(Long courseId, Long versionId) {
    requireCourse(courseId);
    CourseVersion version = courseVersionMapper.selectOne(
      new LambdaQueryWrapper<CourseVersion>()
        .eq(CourseVersion::getCourseId, courseId)
        .eq(CourseVersion::getCourseVersionId, versionId));
    if (version == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5053, "课程版本不存在");
    }
    return toDetail(version);
  }

  private int resolveNextVersionNo(Long courseId) {
    CourseVersion latest = courseVersionMapper.selectOne(
      new LambdaQueryWrapper<CourseVersion>()
        .eq(CourseVersion::getCourseId, courseId)
        .orderByDesc(CourseVersion::getVersionNo)
        .last("LIMIT 1"));
    return latest == null ? 1 : latest.getVersionNo() + 1;
  }

  private void assertVersionMatches(Course course, Integer requestVersion) {
    if (!Objects.equals(course.getVersion(), requestVersion)) {
      throw new BusinessException(HttpStatus.CONFLICT, 2040, "数据已被他人修改，请刷新后重试");
    }
  }

  private Course requireCourse(Long courseId) {
    Course course = courseMapper.selectOne(
      new LambdaQueryWrapper<Course>()
        .eq(Course::getCourseId, courseId));
    if (course == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5011, "课程不存在");
    }
    return course;
  }

  private CourseVersionListItemVO toListItem(CourseVersion version) {
    CourseVersionListItemVO vo = new CourseVersionListItemVO();
    vo.setCourseVersionId(version.getCourseVersionId());
    vo.setCourseId(version.getCourseId());
    vo.setVersionNo(version.getVersionNo());
    vo.setSnapshotHash(version.getSnapshotHash());
    vo.setVersionNote(version.getVersionNote());
    vo.setPublishedBy(version.getPublishedBy());
    vo.setPublishedTime(version.getPublishedTime());
    return vo;
  }

  private CourseVersionVO toDetail(CourseVersion version) {
    CourseVersionVO vo = new CourseVersionVO();
    vo.setCourseVersionId(version.getCourseVersionId());
    vo.setCourseId(version.getCourseId());
    vo.setVersionNo(version.getVersionNo());
    vo.setSnapshotHash(version.getSnapshotHash());
    vo.setVersionNote(version.getVersionNote());
    vo.setPublishedBy(version.getPublishedBy());
    vo.setPublishedTime(version.getPublishedTime());
    vo.setContentSnapshotJson(version.getContentSnapshotJson());
    return vo;
  }
}

package com.sts.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.dto.*;
import com.sts.course.entity.Course;
import com.sts.course.entity.CourseOffering;
import com.sts.course.entity.CourseOfferingEligibleUser;
import com.sts.course.entity.CourseVersion;
import com.sts.course.mapper.CourseMapper;
import com.sts.course.mapper.CourseOfferingEligibleUserMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseVersionMapper;
import com.sts.course.vo.CourseOfferingListItemVO;
import com.sts.course.vo.CourseOfferingVO;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.exam.service.AssessmentReviewerService;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseOfferingServiceImpl extends BaseServiceImpl<CourseOfferingMapper, CourseOffering>
  implements CourseOfferingService {

  private static final String STATUS_DRAFT = "DRAFT";
  private static final String STATUS_PUBLISHED = "PUBLISHED";
  private static final String STATUS_ENDED = "ENDED";
  private static final String STATUS_CANCELLED = "CANCELLED";
  private static final String COURSE_STATUS_PUBLISHED = "PUBLISHED";
  private static final String DEFAULT_COMPLETION_RULE = "ALL_REQUIRED_ITEMS";
  private final CourseOfferingMapper offeringMapper;
  private final CourseMapper courseMapper;
  private final CourseOfferingEligibleUserMapper eligibleUserMapper;
  private final PersonSelectionService personSelectionService;
  private final CourseVersionMapper courseVersionMapper;
  private final CourseSnapshotAssembler snapshotAssembler;
  private final AssessmentExecutionService assessmentExecutionService;
  private final AssessmentReviewerService assessmentReviewerService;

  @Override
  public PageResult<CourseOfferingListItemVO> pageOfferings(PageRequest<CourseOfferingListQuery> request) {
    CourseOfferingListQuery query = request.getQuery();
    // 租户条件由租户插件注入
    LambdaQueryWrapper<CourseOffering> wrapper = new LambdaQueryWrapper<>();
    if (query != null) {
      wrapper.like(StringUtils.hasText(query.getOfferingName()),
                   CourseOffering::getOfferingName,
                   query.getOfferingName())
        .eq(query.getCourseId() != null, CourseOffering::getCourseId, query.getCourseId())
        .eq(StringUtils.hasText(query.getPublishStatus()), CourseOffering::getPublishStatus, query.getPublishStatus())
        .eq(StringUtils.hasText(query.getCourseType()), CourseOffering::getCourseType, query.getCourseType())
        .eq(StringUtils.hasText(query.getEnrollmentType()),
            CourseOffering::getEnrollmentType,
            query.getEnrollmentType())
        .eq(query.getStatus() != null, CourseOffering::getStatus, query.getStatus())
        .eq(query.getPlanContentId() != null, CourseOffering::getPlanContentId, query.getPlanContentId());
    }
    wrapper.orderByDesc(CourseOffering::getCreateTime);
    Page<CourseOffering> page = offeringMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    Map<Long, Course> courseMap = loadCourseMap(
      page.getRecords().stream().map(CourseOffering::getCourseId).distinct().toList());
    List<CourseOfferingListItemVO> list = page.getRecords().stream()
      .map(offering -> toListItem(offering, courseMap.get(offering.getCourseId())))
      .toList();
    return new PageResult<>(list, page.getTotal());
  }

  @Override
  public CourseOfferingVO getOffering(Long offeringId) {
    CourseOffering offering = requireOffering(offeringId);
    Course course = requireCourse(offering.getCourseId());
    return toDetail(offering, course);
  }

  @Override
  @Transactional
  public Long createOffering(CourseOfferingCreateRequest request) {
    requireCourse(request.getCourseId());

    CourseOffering offering = new CourseOffering();
    offering.setOfferingCode(request.getOfferingCode());
    offering.setCourseId(request.getCourseId());
    offering.setOfferingName(request.getOfferingName());
    offering.setStartTime(request.getStartTime());
    offering.setEndTime(request.getEndTime());
    offering.setCourseType(request.getCourseType());
    offering.setEnrollmentType(request.getEnrollmentType());
    offering.setEligibilityType(request.getEligibilityType());
    offering.setCompletionRule(resolveCompletionRule(request.getCompletionRule()));
    offering.setPublishStatus(STATUS_DRAFT);
    offering.setStatus(request.getStatus() != null ? request.getStatus() : 1);

    try {
      offeringMapper.insert(offering);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 5066, "开课编码已存在");
    }
    replaceEligibility(offering, request.getEligibilitySelection());
    return offering.getCourseOfferingId();
  }

  @Override
  @Transactional
  public void updateOffering(Long offeringId, CourseOfferingUpdateRequest request) {
    CourseOffering offering = requireOffering(offeringId);
    if (!STATUS_DRAFT.equals(offering.getPublishStatus()) && !STATUS_PUBLISHED.equals(offering.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5063, "当前开课状态不允许编辑");
    }
    offering.setVersion(request.getVersion());
    offering.setOfferingName(request.getOfferingName());
    offering.setStartTime(request.getStartTime());
    offering.setEndTime(request.getEndTime());
    offering.setCourseType(request.getCourseType());
    offering.setEnrollmentType(request.getEnrollmentType());
    offering.setEligibilityType(request.getEligibilityType());
    if (request.getCompletionRule() != null) {
      offering.setCompletionRule(resolveCompletionRule(request.getCompletionRule()));
    }
    if (request.getStatus() != null) {
      offering.setStatus(request.getStatus());
    }
    offeringMapper.updateByIdWithVersionCheck(offering);
    replaceEligibility(offering, request.getEligibilitySelection());
  }

  @Override
  @Transactional
  public void deleteOffering(Long offeringId) {
    CourseOffering offering = requireOffering(offeringId);
    if (!STATUS_DRAFT.equals(offering.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5064, "仅草稿状态开课可删除");
    }
    this.removeById(offeringId);
  }

  @Override
  @Transactional
  public void publishOffering(Long offeringId, CourseOfferingPublishRequest request) {
    CourseOffering offering = requireOffering(offeringId);
    if (!STATUS_DRAFT.equals(offering.getPublishStatus()) && !STATUS_PUBLISHED.equals(offering.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5063, "仅草稿或已发布开课可发布");
    }
    offering.setVersion(request.getVersion());

    Course course = requireCourse(offering.getCourseId());
    Long versionId = resolvePublishVersionId(offering, course);
    CourseVersion version = courseVersionMapper.selectById(versionId);
    if (version == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5062, "课程无可用版本，不能发布开课");
    }
    // 先写回 courseVersionId，保证下方执行快照按真实版本查找；草稿开课该字段为空时会漏取已配置阅卷人的执行对象。
    offering.setCourseVersionId(versionId);
    var executions = assessmentExecutionService.ensureCourseExecutions(
      offering, snapshotAssembler.parseContentSnapshot(version.getContentSnapshotJson()));
    assessmentReviewerService.assertCourseReviewersConfigured(executions);
    if (STATUS_PUBLISHED.equals(offering.getPublishStatus())) {
      return;
    }
    offering.setCourseVersionId(versionId);
    offering.setPublishStatus(STATUS_PUBLISHED);
    offering.setPublishedTime(LocalDateTime.now());
    offeringMapper.updateByIdWithVersionCheck(offering);
  }

  @Override
  @Transactional
  public void endOffering(Long offeringId, CourseOfferingStatusRequest request) {
    transitionFromPublished(offeringId, request.getVersion(), STATUS_ENDED);
  }

  @Override
  @Transactional
  public void cancelOffering(Long offeringId, CourseOfferingStatusRequest request) {
    transitionFromPublished(offeringId, request.getVersion(), STATUS_CANCELLED);
  }

  private void transitionFromPublished(Long offeringId, Integer version, String targetStatus) {
    CourseOffering offering = requireOffering(offeringId);
    if (!STATUS_PUBLISHED.equals(offering.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5063, "仅已发布开课可执行该操作");
    }
    offering.setVersion(version);
    offering.setPublishStatus(targetStatus);
    offeringMapper.updateByIdWithVersionCheck(offering);
  }

  /**
   * 已有 courseVersionId 则沿用；否则取课程当前已发布版本。
   */
  private Long resolvePublishVersionId(CourseOffering offering, Course course) {
    if (offering.getCourseVersionId() != null) {
      CourseVersion existing = courseVersionMapper.selectById(offering.getCourseVersionId());
      if (existing == null || !offering.getCourseId().equals(existing.getCourseId())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5062, "课程无可用版本，不能发布开课");
      }
      return offering.getCourseVersionId();
    }
    if (!COURSE_STATUS_PUBLISHED.equals(course.getPublishStatus()) || course.getCurrentVersionId() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5062, "课程未发布或无可用版本，不能发布开课");
    }
    return course.getCurrentVersionId();
  }

  private void replaceEligibility(CourseOffering offering, PersonSelectionRequest selection) {
    eligibleUserMapper.delete(new LambdaQueryWrapper<CourseOfferingEligibleUser>()
                                .eq(CourseOfferingEligibleUser::getCourseOfferingId, offering.getCourseOfferingId()));
    if (!"OPEN".equals(offering.getEnrollmentType()) || !"SELECTED".equals(offering.getEligibilityType())) {
      return;
    }
    if (selection == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5067, "指定人员开放报名必须提供人员选择");
    }
    List<Long> userIds = personSelectionService.resolve(selection);
    if (userIds.isEmpty()) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5067, "指定人员开放报名至少需要一名启用用户");
    }
    for (Long userId : userIds) {
      CourseOfferingEligibleUser eligibleUser = new CourseOfferingEligibleUser();
      eligibleUser.setCourseOfferingId(offering.getCourseOfferingId());
      eligibleUser.setUserId(userId);
      eligibleUserMapper.insert(eligibleUser);
    }
  }

  private String resolveCompletionRule(String completionRule) {
    return StringUtils.hasText(completionRule) ? completionRule : DEFAULT_COMPLETION_RULE;
  }

  private Map<Long, Course> loadCourseMap(List<Long> courseIds) {
    if (courseIds.isEmpty()) {
      return Map.of();
    }
    return courseMapper.selectList(
        new LambdaQueryWrapper<Course>()
          .in(Course::getCourseId, courseIds))
      .stream().collect(Collectors.toMap(Course::getCourseId, Function.identity()));
  }

  private CourseOffering requireOffering(Long offeringId) {
    CourseOffering offering = offeringMapper.selectOne(
      new LambdaQueryWrapper<CourseOffering>()
        .eq(CourseOffering::getCourseOfferingId, offeringId));
    if (offering == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5061, "开课不存在");
    }
    return offering;
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

  private CourseOfferingListItemVO toListItem(CourseOffering offering, Course course) {
    CourseOfferingListItemVO vo = new CourseOfferingListItemVO();
    vo.setCourseOfferingId(offering.getCourseOfferingId());
    vo.setOfferingCode(offering.getOfferingCode());
    vo.setCourseId(offering.getCourseId());
    if (course != null) {
      vo.setCourseName(course.getCourseName());
    }
    vo.setCourseVersionId(offering.getCourseVersionId());
    vo.setPlanContentId(offering.getPlanContentId());
    vo.setOfferingName(offering.getOfferingName());
    vo.setStartTime(offering.getStartTime());
    vo.setEndTime(offering.getEndTime());
    vo.setCourseType(offering.getCourseType());
    vo.setEnrollmentType(offering.getEnrollmentType());
    vo.setEligibilityType(offering.getEligibilityType());
    vo.setCompletionRule(offering.getCompletionRule());
    vo.setPublishStatus(offering.getPublishStatus());
    vo.setPublishedTime(offering.getPublishedTime());
    vo.setStatus(offering.getStatus());
    vo.setUpdateTime(offering.getUpdateTime());
    return vo;
  }

  private CourseOfferingVO toDetail(CourseOffering offering, Course course) {
    CourseOfferingVO vo = new CourseOfferingVO();
    vo.setCourseOfferingId(offering.getCourseOfferingId());
    vo.setOfferingCode(offering.getOfferingCode());
    vo.setCourseId(offering.getCourseId());
    vo.setCourseName(course.getCourseName());
    vo.setCourseVersionId(offering.getCourseVersionId());
    vo.setPlanContentId(offering.getPlanContentId());
    vo.setOfferingName(offering.getOfferingName());
    vo.setStartTime(offering.getStartTime());
    vo.setEndTime(offering.getEndTime());
    vo.setCourseType(offering.getCourseType());
    vo.setEnrollmentType(offering.getEnrollmentType());
    vo.setCompletionRule(offering.getCompletionRule());
    vo.setEligibilityType(offering.getEligibilityType());
    vo.setPublishStatus(offering.getPublishStatus());
    vo.setPublishedTime(offering.getPublishedTime());
    vo.setStatus(offering.getStatus());
    vo.setVersion(offering.getVersion());
    vo.setCreateTime(offering.getCreateTime());
    vo.setUpdateTime(offering.getUpdateTime());
    return vo;
  }
}

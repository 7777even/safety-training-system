package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.common.service.BaseServiceImpl;
import com.sts.course.dto.CourseEnrollmentListQuery;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.entity.CourseOffering;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.mapper.CourseOfferingMapper;
import com.sts.course.mapper.CourseOfferingEligibleUserMapper;
import com.sts.course.vo.CourseEnrollmentListItemVO;
import com.sts.person.dto.PersonSelectionRequest;
import com.sts.person.service.PersonSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseEnrollmentServiceImpl extends BaseServiceImpl<CourseEnrollmentMapper, CourseEnrollment>
  implements CourseEnrollmentService {

  private static final String STATUS_PUBLISHED = "PUBLISHED";
  private static final String COURSE_TYPE_ELECTIVE = "ELECTIVE";
  private static final String ENROLLMENT_OPEN = "OPEN";
  private static final String ELIGIBILITY_SELECTED = "SELECTED";
  private static final String SOURCE_ASSIGN = "ASSIGN";
  private static final String SOURCE_SELF = "SELF";
  private static final String LEARNING_NOT_STARTED = "NOT_STARTED";
  private static final String LEARNING_LEARNING = "LEARNING";
  private static final String LEARNING_COMPLETED = "COMPLETED";

  private final CourseEnrollmentMapper enrollmentMapper;
  private final CourseOfferingMapper offeringMapper;
  private final SysUserMapper userMapper;
  private final CourseOfferingEligibleUserMapper eligibleUserMapper;
  private final PersonSelectionService personSelectionService;
  private final com.sts.course.mapper.CourseVersionMapper courseVersionMapper;
  private final CourseSnapshotAssembler snapshotAssembler;

  @Override
  public PageResult<CourseEnrollmentListItemVO> pageEnrollments(Long offeringId,
                                                                PageRequest<CourseEnrollmentListQuery> request) {
    requireOffering(offeringId);

    CourseEnrollmentListQuery query = request.getQuery();
    LambdaQueryWrapper<CourseEnrollment> wrapper = new LambdaQueryWrapper<CourseEnrollment>()
      .eq(CourseEnrollment::getCourseOfferingId, offeringId);
    if (query != null) {
      wrapper.eq(query.getUserId() != null, CourseEnrollment::getUserId, query.getUserId())
        .eq(StringUtils.hasText(query.getLearningStatus()),
            CourseEnrollment::getLearningStatus,
            query.getLearningStatus())
        .eq(StringUtils.hasText(query.getEnrollmentSource()),
            CourseEnrollment::getEnrollmentSource,
            query.getEnrollmentSource());
    }
    wrapper.orderByDesc(CourseEnrollment::getEnrolledTime);

    Page<CourseEnrollment> page = enrollmentMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    Map<Long, SysUser> userMap = loadUserMap(
      page.getRecords().stream().map(CourseEnrollment::getUserId).distinct().toList());
    List<CourseEnrollmentListItemVO> list = page.getRecords().stream()
      .map(enrollment -> toListItem(enrollment, userMap.get(enrollment.getUserId())))
      .toList();
    return new PageResult<>(list, page.getTotal());
  }

  @Override
  @Transactional
  public void assign(Long offeringId, PersonSelectionRequest selection) {
    CourseOffering offering = requireOffering(offeringId);
    assertOfferingEnrollable(offering);
    if (!"ASSIGNED".equals(offering.getEnrollmentType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5072, "仅指派报名开课可分配人员");
    }

    List<Long> userIds = personSelectionService.resolve(selection);

    for (Long userId : userIds) {
      if (findActiveEnrollment(offeringId, userId) != null) {
        continue;
      }
      try {
        enrollmentMapper.insert(buildEnrollment(offeringId, userId, SOURCE_ASSIGN));
      } catch (DuplicateKeyException ignored) {
        // 并发下唯一约束冲突，幂等跳过
      }
    }
  }

  @Override
  @Transactional
  public void removeEnrollment(Long enrollmentId) {
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    if (LEARNING_COMPLETED.equals(enrollment.getLearningStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5073, "已完成报名不可移除");
    }
    this.removeById(enrollmentId);
  }

  @Override
  @Transactional
  public Long selfEnroll(Long offeringId) {
    Long userId = StpUtil.getLoginIdAsLong();
    CourseOffering offering = requireOffering(offeringId);
    assertOfferingEnrollable(offering);
    assertSelfEnrollAllowed(offering);

    CourseEnrollment existing = findActiveEnrollment(offeringId, userId);
    if (existing != null) {
      return existing.getCourseEnrollmentId();
    }

    assertEligibilityAllows(offering, userId);

    CourseEnrollment enrollment = buildEnrollment(offeringId, userId, SOURCE_SELF);
    try {
      enrollmentMapper.insert(enrollment);
    } catch (DuplicateKeyException e) {
      CourseEnrollment duplicate = findActiveEnrollment(offeringId, userId);
      if (duplicate != null) {
        return duplicate.getCourseEnrollmentId();
      }
      throw new BusinessException(HttpStatus.CONFLICT, 5074, "报名已存在");
    }
    return enrollment.getCourseEnrollmentId();
  }

  @Override
  @Transactional
  public void selfWithdraw(Long enrollmentId) {
    Long userId = StpUtil.getLoginIdAsLong();
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    if (!userId.equals(enrollment.getUserId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 5073, "无权退选该报名");
    }

    CourseOffering offering = requireOffering(enrollment.getCourseOfferingId());
    if (!COURSE_TYPE_ELECTIVE.equals(offering.getCourseType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5073, "必修课程不允许自助退选");
    }

    String learningStatus = enrollment.getLearningStatus();
    if (!LEARNING_NOT_STARTED.equals(learningStatus) && !LEARNING_LEARNING.equals(learningStatus)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5073, "当前学习状态不允许退选");
    }

    this.removeById(enrollmentId);
  }

  private void assertOfferingEnrollable(CourseOffering offering) {
    if (!STATUS_PUBLISHED.equals(offering.getPublishStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5072, "当前开课状态不允许报名");
    }
  }

  private void assertSelfEnrollAllowed(CourseOffering offering) {
    if (!ENROLLMENT_OPEN.equals(offering.getEnrollmentType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5072, "当前开课不允许自助报名");
    }
  }

  private void assertEligibilityAllows(CourseOffering offering, Long userId) {
    if (!ELIGIBILITY_SELECTED.equals(offering.getEligibilityType())) {
      return;
    }
    Long count = eligibleUserMapper.selectCount(
      new LambdaQueryWrapper<com.sts.course.entity.CourseOfferingEligibleUser>()
        .eq(com.sts.course.entity.CourseOfferingEligibleUser::getCourseOfferingId,
            offering.getCourseOfferingId())
        .eq(com.sts.course.entity.CourseOfferingEligibleUser::getUserId, userId));
    if (count == null || count == 0) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 4109, "用户不在开放报名白名单内");
    }
  }

  private CourseEnrollment findActiveEnrollment(Long offeringId, Long userId) {
    return enrollmentMapper.selectOne(
      new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseOfferingId, offeringId)
        .eq(CourseEnrollment::getUserId, userId));
  }

  private CourseEnrollment buildEnrollment(Long offeringId, Long userId, String source) {
    CourseEnrollment enrollment = new CourseEnrollment();
    enrollment.setCourseOfferingId(offeringId);
    enrollment.setUserId(userId);
    enrollment.setEnrollmentSource(source);
    enrollment.setEnrolledTime(LocalDateTime.now());
    enrollment.setLearningStatus(LEARNING_NOT_STARTED);
    enrollment.setProgressPercent(BigDecimal.ZERO);
    enrollment.setTotalStudySeconds(0);
    CourseOffering offering = requireOffering(offeringId);
    enrollment.setRecognizedStudySeconds(snapshotAssembler.initialRecognizedStudySeconds(
      courseVersionMapper.selectById(offering.getCourseVersionId())));
    enrollment.setCreditAwarded(BigDecimal.ZERO);
    return enrollment;
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

  private CourseEnrollment requireEnrollment(Long enrollmentId) {
    CourseEnrollment enrollment = enrollmentMapper.selectOne(
      new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseEnrollmentId, enrollmentId));
    if (enrollment == null) {
      throw new BusinessException(HttpStatus.NOT_FOUND, 5071, "报名不存在");
    }
    return enrollment;
  }

  private Map<Long, SysUser> loadUserMap(List<Long> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return userMapper.selectList(
        new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, userIds))
      .stream().collect(Collectors.toMap(SysUser::getUserId, Function.identity()));
  }

  private CourseEnrollmentListItemVO toListItem(CourseEnrollment enrollment, SysUser user) {
    CourseEnrollmentListItemVO vo = new CourseEnrollmentListItemVO();
    vo.setCourseEnrollmentId(enrollment.getCourseEnrollmentId());
    vo.setCourseOfferingId(enrollment.getCourseOfferingId());
    vo.setUserId(enrollment.getUserId());
    if (user != null) {
      vo.setUserCode(user.getUserCode());
      vo.setUserName(user.getUserName());
    }
    vo.setEnrollmentSource(enrollment.getEnrollmentSource());
    vo.setEnrolledTime(enrollment.getEnrolledTime());
    vo.setLearningStatus(enrollment.getLearningStatus());
    vo.setProgressPercent(enrollment.getProgressPercent());
    vo.setTotalStudySeconds(enrollment.getTotalStudySeconds());
    vo.setCreditAwarded(enrollment.getCreditAwarded());
    vo.setCompletedTime(enrollment.getCompletedTime());
    vo.setLastStudyTime(enrollment.getLastStudyTime());
    return vo;
  }
}

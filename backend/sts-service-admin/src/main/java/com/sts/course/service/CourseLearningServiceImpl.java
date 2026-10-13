package com.sts.course.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sts.admin.entity.SysUser;
import com.sts.admin.mapper.SysUserMapper;
import com.sts.admin.tenant.TenantContext;
import com.sts.certificate.service.CertificateAutoIssueService;
import com.sts.common.api.PageRequest;
import com.sts.common.api.PageResult;
import com.sts.common.exception.BusinessException;
import com.sts.course.dto.*;
import com.sts.course.entity.*;
import com.sts.course.mapper.*;
import com.sts.course.snapshot.ChapterSnapshot;
import com.sts.course.snapshot.CourseContentSnapshot;
import com.sts.course.snapshot.ItemSnapshot;
import com.sts.course.snapshot.MaterialRuleSnapshot;
import com.sts.course.vo.*;
import com.sts.exam.entity.ExamAttempt;
import com.sts.exam.entity.ExamCandidate;
import com.sts.exam.mapper.ExamAttemptMapper;
import com.sts.exam.mapper.ExamCandidateMapper;
import com.sts.exam.service.AssessmentExecutionService;
import com.sts.training.event.TrainingParticipantContentFactChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseLearningServiceImpl implements CourseLearningService {

  private static final String STATUS_PUBLISHED = "PUBLISHED";
  private static final String STATUS_ENDED = "ENDED";
  private static final String STATUS_CANCELLED = "CANCELLED";
  private static final String COMPLETION_ALL_REQUIRED = "ALL_REQUIRED_ITEMS";
  private static final String COMPLETION_ALL_ITEMS = "ALL_ITEMS";
  private static final String LEARNING_NOT_STARTED = "NOT_STARTED";
  private static final String LEARNING_LEARNING = "LEARNING";
  private static final String LEARNING_COMPLETED = "COMPLETED";
  private static final String PROGRESS_NOT_STARTED = "NOT_STARTED";
  private static final String PROGRESS_LEARNING = "LEARNING";
  private static final String PROGRESS_COMPLETED = "COMPLETED";
  /** 自定义材料规则来源标记：只有管理员显式保存过材料规则，其要求秒数才覆盖内容项门槛。 */
  private static final String MATERIAL_RULE_SOURCE_CUSTOM = "CUSTOM";
  /** 学习互斥心跳窗口：90 秒无上报视为会话结束，覆盖 3 个 30 秒检查点周期。 */
  private static final int ACTIVE_SESSION_WINDOW_SECONDS = 90;
  private static final int RECENT_SESSION_LIMIT = 20;

  private final CourseEnrollmentMapper enrollmentMapper;
  private final CourseOfferingMapper offeringMapper;
  private final CourseVersionMapper courseVersionMapper;
  private final CourseItemProgressMapper itemProgressMapper;
  private final CourseStudySessionMapper studySessionMapper;
  private final CourseEvaluationMapper evaluationMapper;
  private final SysUserMapper userMapper;
  private final CourseSnapshotAssembler snapshotAssembler;
  private final CourseOfferingEligibleUserMapper eligibleUserMapper;
  private final LearnerCourseResourceAssembler resourceAssembler;
  private final AssessmentExecutionService assessmentExecutionService;
  private final CourseAssessmentResultMapper assessmentResultMapper;
  private final ExamCandidateMapper examCandidateMapper;
  private final ExamAttemptMapper examAttemptMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final CertificateAutoIssueService certificateAutoIssueService;
  private final CourseCategoryService categoryService;

  @Override
  public PageResult<LearnerCourseEnrollmentVO> pageMyEnrollments(
    PageRequest<LearnerCourseEnrollmentQuery> request) {
    Long userId = StpUtil.getLoginIdAsLong();
    LearnerCourseEnrollmentQuery query = request.getQuery();

    LambdaQueryWrapper<CourseEnrollment> wrapper = new LambdaQueryWrapper<CourseEnrollment>()
      .eq(CourseEnrollment::getUserId, userId)
      .ne(CourseEnrollment::getEnrollmentSource, "TRAINING")
      .eq(query != null && org.springframework.util.StringUtils.hasText(query.getLearningStatus()),
          CourseEnrollment::getLearningStatus,
          query == null ? null : query.getLearningStatus())
      .orderByDesc(CourseEnrollment::getLastStudyTime)
      .orderByDesc(CourseEnrollment::getEnrolledTime)
      .orderByDesc(CourseEnrollment::getCourseEnrollmentId);

    Page<CourseEnrollment> page = enrollmentMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
    if (page.getRecords().isEmpty()) {
      return new PageResult<>(List.of(), page.getTotal());
    }

    Map<Long, CourseOffering> offerings = offeringMapper.selectBatchIds(
        page.getRecords().stream().map(CourseEnrollment::getCourseOfferingId).distinct().toList())
      .stream()
      .collect(Collectors.toMap(CourseOffering::getCourseOfferingId, Function.identity()));
    Map<Long, CourseVersion> versions = loadVersions(offerings.values());

    List<LearnerCourseEnrollmentVO> list = page.getRecords().stream()
      .map(enrollment -> toLearnerEnrollment(enrollment, offerings, versions))
      .toList();
    return new PageResult<>(list, page.getTotal());
  }

  @Override
  public PageResult<LearnerCourseOfferingVO> pageLearnerOfferings(
    PageRequest<LearnerCourseOfferingQuery> request) {
    Long userId = StpUtil.getLoginIdAsLong();
    LearnerCourseOfferingQuery query = request.getQuery();
    if (query != null && org.springframework.util.StringUtils.hasText(query.getOfferingStatus())
      && !"AVAILABLE".equals(query.getOfferingStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "开课状态参数错误");
    }

    LocalDateTime now = LocalDateTime.now();
    List<CourseOffering> candidates = offeringMapper.selectList(
      new LambdaQueryWrapper<CourseOffering>()
        .eq(CourseOffering::getPublishStatus, STATUS_PUBLISHED)
        .eq(CourseOffering::getStatus, 1)
        .eq(CourseOffering::getEnrollmentType, "OPEN")
        .and(w -> w.isNull(CourseOffering::getStartTime).or().le(CourseOffering::getStartTime, now))
        .and(w -> w.isNull(CourseOffering::getEndTime).or().ge(CourseOffering::getEndTime, now))
        .orderByAsc(CourseOffering::getStartTime)
        .orderByDesc(CourseOffering::getCourseOfferingId));
    if (candidates.isEmpty()) {
      return new PageResult<>(List.of(), 0);
    }

    Set<Long> enrolledOfferingIds = new HashSet<>(enrollmentMapper.selectList(
        new LambdaQueryWrapper<CourseEnrollment>()
          .eq(CourseEnrollment::getUserId, userId)
          .in(CourseEnrollment::getCourseOfferingId,
              candidates.stream().map(CourseOffering::getCourseOfferingId).toList()))
                                                    .stream().map(CourseEnrollment::getCourseOfferingId).toList());
    Map<Long, CourseVersion> versions = loadVersions(candidates);
    Set<Long> selectedCategories = query == null || query.getCategoryId() == null
      ? Set.of() : categoryAndDescendants(query.getCategoryId());

    List<LearnerCourseOfferingVO> eligible = candidates.stream()
      .filter(offering -> !enrolledOfferingIds.contains(offering.getCourseOfferingId()))
      .filter(offering -> isEligibleForOpenEnrollment(offering, userId))
      .filter(offering -> matchesOfferingQuery(
        offering, versions.get(offering.getCourseVersionId()), query, selectedCategories))
      .map(offering -> toLearnerOffering(offering, versions))
      .toList();

    int from = Math.min((request.getPageNum() - 1) * request.getPageSize(), eligible.size());
    int to = Math.min(from + request.getPageSize(), eligible.size());
    return new PageResult<>(eligible.subList(from, to), eligible.size());
  }

  private boolean isEligibleForOpenEnrollment(CourseOffering offering, Long userId) {
    if (!"SELECTED".equals(offering.getEligibilityType())) {
      return true;
    }
    Long count = eligibleUserMapper.selectCount(new LambdaQueryWrapper<CourseOfferingEligibleUser>()
                                                  .eq(CourseOfferingEligibleUser::getCourseOfferingId,
                                                      offering.getCourseOfferingId())
                                                  .eq(CourseOfferingEligibleUser::getUserId, userId));
    return count != null && count > 0;
  }

  private Map<Long, CourseVersion> loadVersions(Collection<CourseOffering> offerings) {
    List<Long> versionIds = offerings.stream()
      .map(CourseOffering::getCourseVersionId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
    if (versionIds.isEmpty()) {
      return Map.of();
    }
    return courseVersionMapper.selectBatchIds(versionIds).stream()
      .collect(Collectors.toMap(CourseVersion::getCourseVersionId, Function.identity()));
  }

  private LearnerCourseEnrollmentVO toLearnerEnrollment(
    CourseEnrollment enrollment,
    Map<Long, CourseOffering> offerings,
    Map<Long, CourseVersion> versions) {
    CourseOffering offering = offerings.get(enrollment.getCourseOfferingId());
    if (offering == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "报名关联开课或课程版本快照不存在");
    }
    CourseContentSnapshot snapshot = requireSnapshot(versions.get(offering.getCourseVersionId()));
    if (snapshot.getCourse() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }

    LearnerCourseEnrollmentVO vo = new LearnerCourseEnrollmentVO();
    vo.setEnrollmentId(enrollment.getCourseEnrollmentId());
    vo.setOfferingId(offering.getCourseOfferingId());
    vo.setCourseId(offering.getCourseId());
    vo.setCourseName(snapshot.getCourse().getCourseName());
    vo.setOfferingName(offering.getOfferingName());
    vo.setCourseType(offering.getCourseType());
    vo.setStartTime(offering.getStartTime());
    vo.setEndTime(offering.getEndTime());
    vo.setLearningStatus(enrollment.getLearningStatus());
    vo.setProgressPercent(enrollment.getProgressPercent());
    vo.setLastStudyTime(enrollment.getLastStudyTime());
    vo.setCompletedTime(enrollment.getCompletedTime());
    vo.setCoverUrl(courseCoverUrl(offering.getCourseId(), snapshot.getCourse().getCoverFileId()));
    return vo;
  }

  private LearnerCourseOfferingVO toLearnerOffering(
    CourseOffering offering,
    Map<Long, CourseVersion> versions) {
    CourseContentSnapshot snapshot = requireSnapshot(versions.get(offering.getCourseVersionId()));
    if (snapshot.getCourse() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }

    LearnerCourseOfferingVO vo = new LearnerCourseOfferingVO();
    vo.setOfferingId(offering.getCourseOfferingId());
    vo.setCourseId(offering.getCourseId());
    vo.setCourseName(snapshot.getCourse().getCourseName());
    vo.setOfferingName(offering.getOfferingName());
    vo.setCourseType(offering.getCourseType());
    vo.setStartTime(offering.getStartTime());
    vo.setEndTime(offering.getEndTime());
    vo.setEnrollmentType(offering.getEnrollmentType());
    vo.setEnrollmentAvailability("AVAILABLE");
    vo.setCoverUrl(courseCoverUrl(offering.getCourseId(), snapshot.getCourse().getCoverFileId()));
    return vo;
  }

  @Override
  public List<com.sts.course.vo.CourseCategoryTreeVO> learnerCategories() {
    StpUtil.getLoginIdAsLong();
    return categoryService.getCategoryTree(null, 1);
  }

  /** 按启用分类树展开父分类，随后沿原有资格过滤与分页流程处理。 */
  private Set<Long> categoryAndDescendants(Long categoryId) {
    Set<Long> result = new HashSet<>();
    java.util.ArrayDeque<com.sts.course.vo.CourseCategoryTreeVO> pending = new java.util.ArrayDeque<>(learnerCategories());
    while (!pending.isEmpty()) {
      var node = pending.removeFirst();
      if (categoryId.equals(node.getCourseCategoryId()) || result.contains(node.getParentId())) {
        result.add(node.getCourseCategoryId());
      }
      pending.addAll(node.getChildren());
    }
    return result;
  }

  private boolean matchesOfferingQuery(
    CourseOffering offering,
    CourseVersion version,
    LearnerCourseOfferingQuery query, Set<Long> selectedCategories) {
    if (query == null) {
      return true;
    }
    if (org.springframework.util.StringUtils.hasText(query.getKeyword())) {
      String keyword = query.getKeyword().trim();
      CourseContentSnapshot snapshot = requireSnapshot(version);
      String courseName = snapshot.getCourse() == null ? null : snapshot.getCourse().getCourseName();
      if (!(containsIgnoreCase(courseName, keyword)
        || containsIgnoreCase(offering.getOfferingName(), keyword)
        || containsIgnoreCase(snapshot.getCourse().getSyllabus(), keyword))) {
        return false;
      }
    }
    if (query.getCategoryId() != null) {
      CourseContentSnapshot snapshot = requireSnapshot(version);
      return snapshot.getCategoryIds() != null && snapshot.getCategoryIds().stream().anyMatch(selectedCategories::contains);
    }
    return true;
  }

  private boolean containsIgnoreCase(String source, String keyword) {
    return source != null && source.toLowerCase(java.util.Locale.ROOT)
      .contains(keyword.toLowerCase(java.util.Locale.ROOT));
  }

  private CourseContentSnapshot requireSnapshot(CourseVersion version) {
    if (version == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }
    CourseContentSnapshot snapshot = snapshotAssembler.parseContentSnapshot(version.getContentSnapshotJson());
    if (snapshot == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }
    return snapshot;
  }

  @Override
  public LearnerCourseTasksVO getMyEnrollmentTasks(Long enrollmentId) {
    Long userId = StpUtil.getLoginIdAsLong();
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    assertEnrollmentOwner(enrollment, userId);
    CourseOffering offering = requireOffering(enrollment.getCourseOfferingId());
    CourseContentSnapshot snapshot = loadSnapshot(offering);
    if (snapshot.getCourse() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }

    Map<Long, CourseItemProgress> progresses = itemProgressMapper.selectList(
        new LambdaQueryWrapper<CourseItemProgress>()
          .eq(CourseItemProgress::getCourseEnrollmentId, enrollmentId))
      .stream().collect(Collectors.toMap(CourseItemProgress::getCourseItemId, Function.identity()));

    LearnerCourseTasksVO vo = new LearnerCourseTasksVO();
    vo.setEnrollmentId(enrollmentId);
    vo.setOfferingId(offering.getCourseOfferingId());
    vo.setCourseId(offering.getCourseId());
    vo.setCourseName(snapshot.getCourse().getCourseName());
    vo.setOfferingName(offering.getOfferingName());
    vo.setCourseType(offering.getCourseType());
    vo.setStartTime(offering.getStartTime());
    vo.setEndTime(offering.getEndTime());
    vo.setLearningStatus(enrollment.getLearningStatus());
    vo.setProgressPercent(enrollment.getProgressPercent());
    vo.setCoverUrl(courseCoverUrl(offering.getCourseId(), snapshot.getCourse().getCoverFileId()));
    vo.setChapters((snapshot.getChapters() == null ? List.<ChapterSnapshot>of() : snapshot.getChapters())
                     .stream()
                     .sorted(Comparator.comparing(
                         ChapterSnapshot::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                               .thenComparing(ChapterSnapshot::getChapterId, Comparator.nullsLast(Long::compareTo)))
                     .map(chapter -> toTaskChapter(chapter, enrollment, offering, progresses))
                     .toList());
    for (var chapter : vo.getChapters()) {
      for (var item : chapter.getItems()) {
        if (Integer.valueOf(1).equals(item.getIsFinalAssessment())
          && !finalAssessmentReady(snapshot, offering, progresses, item.getCourseItemId())) {
          item.setLearnable(false);
          item.setUnavailableReason("LEARNING_REQUIRED");
        }
      }
    }
    applyExamTaskStates(vo, enrollmentId);
    return vo;
  }

  private void applyExamTaskStates(LearnerCourseTasksVO tasks, Long enrollmentId) {
    Map<Long, ExamCandidate> candidatesByItem = examCandidateMapper.selectList(
        new LambdaQueryWrapper<ExamCandidate>()
          .eq(ExamCandidate::getCourseEnrollmentId, enrollmentId))
      .stream()
      .filter(candidate -> candidate.getCourseItemId() != null)
      .collect(Collectors.toMap(ExamCandidate::getCourseItemId, Function.identity(),
                                (left, right) -> right.getExamCandidateId() > left.getExamCandidateId() ? right : left));
    Map<Long, List<ExamAttempt>> attemptsByCandidate = candidatesByItem.isEmpty() ? Map.of() :
      examAttemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                                     .in(ExamAttempt::getExamCandidateId,
                                         candidatesByItem.values().stream().map(ExamCandidate::getExamCandidateId).toList())
                                     .orderByDesc(ExamAttempt::getAttemptNo)
                                     .orderByDesc(ExamAttempt::getExamAttemptId))
        .stream().collect(Collectors.groupingBy(ExamAttempt::getExamCandidateId));
    for (LearnerCourseTaskChapterVO chapter : tasks.getChapters()) {
      for (LearnerCourseTaskItemVO item : chapter.getItems()) {
        if (isExamTaskItem(item)) {
          item.setExamTaskStatus("NOT_STARTED");
          item.setScorePublishStatus("UNPUBLISHED");
          ExamCandidate candidate = candidatesByItem.get(item.getCourseItemId());
          if (candidate == null) continue;
          List<ExamAttempt> attempts = attemptsByCandidate.get(candidate.getExamCandidateId());
          if (attempts == null || attempts.isEmpty()) continue;
          // 课程测验只允许一个终态结果；历史重复数据优先回到已生效的那份结果。
          ExamAttempt effective = attempts.stream()
            .filter(attempt -> "RESULT_EFFECTIVE".equals(attempt.getAttemptStatus()))
            .findFirst().orElse(null);
          ExamAttempt answering = attempts.stream()
            .filter(attempt -> "ANSWERING".equals(attempt.getAttemptStatus()))
            .findFirst().orElse(null);
          ExamAttempt latest = attempts.getFirst();
          if (Integer.valueOf(1).equals(item.getIsFinalAssessment())) {
            effective = "RESULT_EFFECTIVE".equals(latest.getAttemptStatus()) ? latest : null;
            item.setCanRetake(effective != null && Integer.valueOf(0).equals(effective.getIsPassed())
              && candidate.getAttemptCount() < 2 && Boolean.TRUE.equals(item.getLearnable()));
          }
          if (effective != null && "QUIZ".equals(item.getItemType())) {
            item.setExamTaskStatus("RESULT_EFFECTIVE");
            item.setPublishedExamAttemptId(effective.getExamAttemptId());
            item.setScorePublishStatus("EFFECTIVE");
            item.setFinalScore(effective.getFinalScore());
            item.setIsPassed(effective.getIsPassed());
          } else if (answering != null) {
            item.setExamTaskStatus("IN_PROGRESS");
            item.setActiveExamAttemptId(answering.getExamAttemptId());
          } else if (effective != null) {
            item.setExamTaskStatus("RESULT_EFFECTIVE");
            item.setPublishedExamAttemptId(effective.getExamAttemptId());
            item.setScorePublishStatus("EFFECTIVE");
            item.setFinalScore(effective.getFinalScore());
            item.setIsPassed(effective.getIsPassed());
          } else {
            // PENDING_REVIEW 等内部状态统一收敛为课程契约的等待结果状态。
            item.setExamTaskStatus("WAITING_RESULT");
            item.setActiveExamAttemptId(latest.getExamAttemptId());
            item.setScorePublishStatus("PENDING_REVIEW");
          }
        }
      }
    }
  }

  private boolean isExamTaskItem(LearnerCourseTaskItemVO item) {
    return "QUIZ".equals(item.getItemType()) || "QUESTION_BANK".equals(item.getItemType());
  }

  private LearnerCourseTaskChapterVO toTaskChapter(
    ChapterSnapshot chapter,
    CourseEnrollment enrollment,
    CourseOffering offering,
    Map<Long, CourseItemProgress> progresses) {
    LearnerCourseTaskChapterVO vo = new LearnerCourseTaskChapterVO();
    vo.setChapterId(chapter.getChapterId());
    vo.setParentId(chapter.getParentId());
    vo.setChapterName(chapter.getChapterName());
    vo.setSortOrder(chapter.getSortOrder());
    vo.setItems((chapter.getItems() == null ? List.<ItemSnapshot>of() : chapter.getItems())
                  .stream()
                  .sorted(Comparator.comparing(
                      ItemSnapshot::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(ItemSnapshot::getCourseItemId, Comparator.nullsLast(Long::compareTo)))
                  .map(item -> toTaskItem(item, chapter, enrollment, offering, progresses.get(item.getCourseItemId())))
                  .toList());
    return vo;
  }

  private LearnerCourseTaskItemVO toTaskItem(
    ItemSnapshot item,
    ChapterSnapshot chapter,
    CourseEnrollment enrollment,
    CourseOffering offering,
    CourseItemProgress progress) {
    LearnerCourseTaskItemVO vo = new LearnerCourseTaskItemVO();
    vo.setCourseItemId(item.getCourseItemId());
    vo.setItemType(item.getItemType());
    vo.setItemTitle(item.getItemTitle());
    vo.setIsRequired(item.getIsRequired());
    vo.setSortOrder(item.getSortOrder());
    vo.setAssetId(item.getAssetId());
    vo.setLinkedQuizItemId(item.getLinkedQuizItemId());
    vo.setIsFinalAssessment(item.getIsFinalAssessment());
    vo.setPassScore(item.getPassScore());
    vo.setQuestionBankId(item.getQuestionBankId());
    vo.setPaperVersionId(item.getPaperVersionId());
    vo.setProgressStatus(progress == null ? PROGRESS_NOT_STARTED : progress.getProgressStatus());
    vo.setStudySeconds(progress == null || progress.getStudySeconds() == null ? 0 : progress.getStudySeconds());
    if (progress != null) {
      vo.setLastPositionSeconds(progress.getLastPositionSeconds());
      vo.setLastPageNo(progress.getLastPageNo());
      vo.setCompletedTime(progress.getCompletedTime());
      vo.setLastStudyTime(progress.getLastStudyTime());
    }
    vo.setLiveSession(toLiveReference(item.getLiveSession()));

    String unavailableReason = resolveUnavailableReason(enrollment, offering, chapter, item);
    vo.setLearnable(unavailableReason == null);
    vo.setUnavailableReason(unavailableReason);
    return vo;
  }

  private String resolveUnavailableReason(
    CourseEnrollment enrollment,
    CourseOffering offering,
    ChapterSnapshot chapter,
    ItemSnapshot item) {
    if (STATUS_CANCELLED.equals(offering.getPublishStatus())) {
      return "OFFERING_CANCELLED";
    }
    if ("WITHDRAWN".equals(enrollment.getLearningStatus())) {
      return "ENROLLMENT_WITHDRAWN";
    }
    if ("EXPIRED".equals(enrollment.getLearningStatus())) {
      return "ENROLLMENT_EXPIRED";
    }
    if (!Integer.valueOf(1).equals(chapter.getStatus())
      || !Integer.valueOf(1).equals(item.getStatus())) {
      return "CONTENT_DISABLED";
    }
    return null;
  }

  private LearnerLiveTaskReferenceVO toLiveReference(
    com.sts.course.snapshot.LiveSessionSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    LearnerLiveTaskReferenceVO vo = new LearnerLiveTaskReferenceVO();
    vo.setLiveSessionId(snapshot.getLiveSessionId());
    vo.setLiveName(snapshot.getLiveName());
    vo.setTeacherUserId(snapshot.getTeacherUserId());
    vo.setLiveUrl(snapshot.getLiveUrl());
    vo.setStartTime(snapshot.getStartTime());
    vo.setEndTime(snapshot.getEndTime());
    vo.setLiveStatus(snapshot.getLiveStatus());
    return vo;
  }

  @Override
  @Transactional
  public Long reportSession(StudySessionReportRequest request) {
    Long userId = StpUtil.getLoginIdAsLong();

    CourseEnrollment enrollment = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
      .eq(CourseEnrollment::getCourseEnrollmentId, request.getEnrollmentId()).last("FOR UPDATE"));
    if (enrollment == null) throw new BusinessException(HttpStatus.NOT_FOUND, 5071, "报名不存在");
    assertEnrollmentOwner(enrollment, userId);

    CourseOffering offering = requireOffering(enrollment.getCourseOfferingId());
    assertOfferingLearnable(offering);

    CourseContentSnapshot snapshot = loadSnapshot(offering);
    ItemSnapshot item = findItem(snapshot, request.getCourseItemId());
    if (item == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5084, "内容项不在课程快照中");
    }

    if ("LIVE".equals(item.getItemType())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5086, "直播请使用参与入口，不能上报外部观看时长");
    }
    CourseStudySession existing = findSessionByTrace(request.getEnrollmentId(), request.getClientTraceId());
    if (existing != null) {
      return existing.getStudySessionId();
    }

    assertNoParallelLearning(userId, request);

    CourseStudySession session = buildSession(userId, request);
    try {
      studySessionMapper.insert(session);
    } catch (DuplicateKeyException e) {
      CourseStudySession duplicate = findSessionByTrace(request.getEnrollmentId(), request.getClientTraceId());
      if (duplicate != null) {
        return duplicate.getStudySessionId();
      }
      throw e;
    }

    upsertItemProgress(enrollment, item, request);
    recomputeEnrollment(enrollment, offering, snapshot);

    return session.getStudySessionId();
  }

  /** 本人直播参与，成功登记不等于完整观看。 */
  @Override
  @Transactional
  public com.sts.course.vo.LiveParticipationVO participateLive(Long enrollmentId, Long itemId) {
    CourseEnrollment enrollment = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
      .eq(CourseEnrollment::getCourseEnrollmentId, enrollmentId).last("FOR UPDATE"));
    if (enrollment == null) throw new BusinessException(HttpStatus.NOT_FOUND, 5071, "报名不存在");
    assertEnrollmentOwner(enrollment, StpUtil.getLoginIdAsLong());
    CourseOffering offering = requireOffering(enrollment.getCourseOfferingId());
    assertOfferingLearnable(offering);
    CourseContentSnapshot snapshot = loadSnapshot(offering);
    ItemSnapshot item = findItem(snapshot, itemId);
    if (!Set.of(LEARNING_NOT_STARTED, LEARNING_LEARNING, LEARNING_COMPLETED).contains(enrollment.getLearningStatus())
      || item == null || !"LIVE".equals(item.getItemType()) || !Integer.valueOf(1).equals(item.getStatus())
      || snapshot.getChapters().stream().noneMatch(c -> Integer.valueOf(1).equals(c.getStatus())
        && c.getItems() != null && c.getItems().stream().anyMatch(i -> Objects.equals(i.getCourseItemId(), itemId)))) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 5086, "当前直播不可参加");
    }
    var live = snapshotAssembler.currentLiveSession(itemId);
    LocalDateTime now = LocalDateTime.now();
    boolean safeUrl;
    try {
      var uri = java.net.URI.create(live.getLiveUrl());
      safeUrl = Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
    } catch (RuntimeException ex) { safeUrl = false; }
    if (!safeUrl || !Integer.valueOf(1).equals(live.getStatus())
      || !Set.of("SCHEDULED", "LIVING").contains(live.getLiveStatus() == null ? "" : live.getLiveStatus())
      || live.getStartTime() == null || live.getEndTime() == null
      || now.isBefore(live.getStartTime()) || now.isAfter(live.getEndTime())) {
      throw new BusinessException(HttpStatus.CONFLICT, 5086, "直播未开放、已结束或已取消");
    }
    CourseItemProgress progress = itemProgressMapper.selectOne(new LambdaQueryWrapper<CourseItemProgress>()
      .eq(CourseItemProgress::getCourseEnrollmentId, enrollmentId).eq(CourseItemProgress::getCourseItemId, itemId).last("FOR UPDATE"));
    boolean created = progress == null;
    if (created) {
      progress = new CourseItemProgress();
      progress.setCourseEnrollmentId(enrollmentId);
      progress.setCourseItemId(itemId);
      progress.setFirstStartTime(now);
      progress.setAttemptCount(1);
    }
    if (!PROGRESS_COMPLETED.equals(progress.getProgressStatus())) {
      progress.setProgressStatus(PROGRESS_COMPLETED);
      progress.setCompletedTime(now);
      progress.setLastStudyTime(now);
      progress.setStudySeconds(0);
      if (created) itemProgressMapper.insert(progress); else itemProgressMapper.updateById(progress);
      recomputeEnrollment(enrollment, offering, snapshot);
    }
    var vo = new com.sts.course.vo.LiveParticipationVO();
    vo.setLiveUrl(live.getLiveUrl());
    vo.setProgressStatus(PROGRESS_COMPLETED);
    return vo;
  }

  @Override
  public CourseEnrollmentDetailVO getMyEnrollmentDetail(Long enrollmentId) {
    Long userId = StpUtil.getLoginIdAsLong();
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    assertEnrollmentOwner(enrollment, userId);
    return buildEnrollmentDetail(enrollment, false);
  }

  /** 评价查询范围只来自本人报名，不接受客户端传入的开课或用户标识。 */
  @Override
  public PageResult<CourseEvaluationListItemVO> pagePeerEvaluations(Long enrollmentId, PageRequest<Void> request) {
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    assertEnrollmentOwner(enrollment, StpUtil.getLoginIdAsLong());
    if ("WITHDRAWN".equals(enrollment.getLearningStatus())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "退选报名无权查看课程评价");
    }
    if (request.getPageNum() < 1 || request.getPageSize() < 1 || request.getPageSize() > 100) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "分页参数错误");
    }
    Page<CourseEvaluation> page = evaluationMapper.selectPage(
      new Page<>(request.getPageNum(), request.getPageSize()),
      new LambdaQueryWrapper<CourseEvaluation>()
        .eq(CourseEvaluation::getCourseOfferingId, enrollment.getCourseOfferingId())
        .eq(CourseEvaluation::getStatus, 1)
        .orderByDesc(CourseEvaluation::getSubmittedTime)
        .orderByDesc(CourseEvaluation::getCourseEvaluationId));
    List<Long> namedUserIds = page.getRecords().stream()
      .filter(item -> !Integer.valueOf(1).equals(item.getIsAnonymous()))
      .map(CourseEvaluation::getUserId).distinct().toList();
    Map<Long, SysUser> users = namedUserIds.isEmpty() ? Map.of() : userMapper.selectList(
      new LambdaQueryWrapper<SysUser>().in(SysUser::getUserId, namedUserIds))
      .stream().collect(Collectors.toMap(SysUser::getUserId, Function.identity()));
    List<CourseEvaluationListItemVO> items = page.getRecords().stream().map(item -> {
      CourseEvaluationListItemVO vo = new CourseEvaluationListItemVO();
      boolean anonymous = Integer.valueOf(1).equals(item.getIsAnonymous());
      vo.setRating(item.getRating());
      vo.setContent(item.getContent());
      vo.setIsAnonymous(item.getIsAnonymous());
      SysUser user = users.get(item.getUserId());
      vo.setDisplayName(anonymous ? "匿名学员" : user == null ? "学员" : user.getUserName());
      vo.setSubmittedTime(item.getSubmittedTime() == null ? null : anonymous
        ? item.getSubmittedTime().toLocalDate().toString() : item.getSubmittedTime().toString());
      return vo;
    }).toList();
    return new PageResult<>(items, page.getTotal());
  }

  @Override
  public CourseEnrollmentDetailVO getLearningDetail(Long enrollmentId) {
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    return buildEnrollmentDetail(enrollment, true);
  }

  @Override
  @Transactional
  public void submitEvaluation(Long enrollmentId, CourseEvaluationSubmitRequest request) {
    Long userId = StpUtil.getLoginIdAsLong();
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    assertEnrollmentOwner(enrollment, userId);

    if (!LEARNING_COMPLETED.equals(enrollment.getLearningStatus())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5082, "仅已完成学习可提交评价");
    }

    if (findEvaluation(enrollmentId) != null) {
      throw new BusinessException(HttpStatus.CONFLICT, 5085, "该报名已提交评价");
    }

    CourseEvaluation evaluation = new CourseEvaluation();
    evaluation.setCourseOfferingId(enrollment.getCourseOfferingId());
    evaluation.setCourseEnrollmentId(enrollmentId);
    evaluation.setUserId(userId);
    evaluation.setRating(request.getRating());
    evaluation.setContent(request.getContent());
    evaluation.setIsAnonymous(request.getIsAnonymous());
    evaluation.setSubmittedTime(LocalDateTime.now());
    evaluation.setStatus(1);
    try {
      evaluationMapper.insert(evaluation);
    } catch (DuplicateKeyException e) {
      throw new BusinessException(HttpStatus.CONFLICT, 5085, "该报名已提交评价");
    }
  }

  /**
   * 课程测验交卷即投影内容项进度，让学员和教师在成绩生效前就能看到“已参加、待批阅”的事实。
   *
   * <p>本方法只写内容项进度，不写 {@code course_assessment_result}：生效事实仍由
   * {@link #applyEffectiveAssessmentResult} 单独写入并承担幂等，避免交卷提前占用唯一键导致
   * 成绩生效回调被短路、内容项永远无法转为已完成。</p>
   */
  @Override
  @Transactional
  public void applySubmittedAssessmentResult(CourseAssessmentSubmitted result) {
    if (result == null || result.organizationId() == null
      || result.assessmentExecutionId() == null || result.courseEnrollmentId() == null
      || result.courseOfferingId() == null || result.courseItemId() == null
      || result.submittedTime() == null
      || !"COURSE_QUIZ".equals(result.sourceType())) {
      return;
    }
    TenantContext.runWithTenant(result.organizationId(), () -> {
      CourseEnrollment enrollment = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseEnrollmentId, result.courseEnrollmentId()).last("FOR UPDATE"));
      if (enrollment == null) throw new BusinessException(HttpStatus.NOT_FOUND, 5081, "课程报名不存在");
      if (!Objects.equals(enrollment.getOrganizationId(), result.organizationId())
        || !Objects.equals(enrollment.getUserId(), result.userId())
        || !Objects.equals(enrollment.getCourseOfferingId(), result.courseOfferingId())) {
        throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "考核结果不属于指定课程报名");
      }
      CourseOffering offering = requireOffering(result.courseOfferingId());
      CourseContentSnapshot snapshot = loadSnapshot(offering);
      ItemSnapshot item = findItem(snapshot, result.courseItemId());
      if (item == null || !"QUIZ".equals(item.getItemType())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5084, "考核结果不属于课程内容项");
      }
      var execution = assessmentExecutionService.requireCourse(
        offering.getCourseOfferingId(), offering.getCourseVersionId(), item.getCourseItemId());
      if (!Objects.equals(execution.getAssessmentExecutionId(), result.assessmentExecutionId())
        || !Objects.equals(execution.getSourceType(), result.sourceType())) {
        throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "考核执行与课程报名不匹配");
      }

      CourseItemProgress progress = itemProgressMapper.selectOne(
        new LambdaQueryWrapper<CourseItemProgress>()
          .eq(CourseItemProgress::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())
          .eq(CourseItemProgress::getCourseItemId, item.getCourseItemId()).last("FOR UPDATE"));
      if (progress == null) {
        progress = new CourseItemProgress();
        progress.setCourseEnrollmentId(enrollment.getCourseEnrollmentId());
        progress.setCourseItemId(item.getCourseItemId());
        progress.setProgressStatus(PROGRESS_LEARNING);
        progress.setStudySeconds(0);
        // 一次交卷记一次作答，成绩生效回调不再重复累加该次作答。
        progress.setAttemptCount(1);
        progress.setFirstStartTime(result.submittedTime());
        progress.setLastStudyTime(result.submittedTime());
        itemProgressMapper.insert(progress);
      } else {
        // 重复回调只刷新最近学习时间，作答次数与完成状态保持幂等。
        progress.setLastStudyTime(result.submittedTime());
        itemProgressMapper.updateById(progress);
      }
      recomputeEnrollment(enrollment, offering, snapshot);
    });
  }

  @Override
  @Transactional
  public void applyEffectiveAssessmentResult(CourseAssessmentResultEffective result) {
    if (result == null || result.organizationId() == null
      || result.assessmentExecutionId() == null || result.courseEnrollmentId() == null
      || result.courseOfferingId() == null || result.courseItemId() == null
      || result.effectiveTime() == null
      || !Set.of("COURSE_QUIZ", "COURSE_BANK_PRACTICE").contains(result.sourceType())) {
      return;
    }
    TenantContext.runWithTenant(result.organizationId(), () -> {
      CourseEnrollment enrollment = enrollmentMapper.selectOne(new LambdaQueryWrapper<CourseEnrollment>()
        .eq(CourseEnrollment::getCourseEnrollmentId, result.courseEnrollmentId()).last("FOR UPDATE"));
      if (enrollment == null) throw new BusinessException(HttpStatus.NOT_FOUND, 5081, "课程报名不存在");
      if (!Objects.equals(enrollment.getOrganizationId(), result.organizationId())
        || !Objects.equals(enrollment.getUserId(), result.userId())
        || !Objects.equals(enrollment.getCourseOfferingId(), result.courseOfferingId())) {
        throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "考核结果不属于指定课程报名");
      }
      CourseOffering offering = requireOffering(result.courseOfferingId());
      CourseContentSnapshot snapshot = loadSnapshot(offering);
      ItemSnapshot item = findItem(snapshot, result.courseItemId());
      String expectedItemType = "COURSE_QUIZ".equals(result.sourceType()) ? "QUIZ" : "QUESTION_BANK";
      if (item == null || !expectedItemType.equals(item.getItemType())) {
        throw new BusinessException(HttpStatus.BAD_REQUEST, 5084, "考核结果不属于课程内容项");
      }
      var execution = assessmentExecutionService.requireCourse(
        offering.getCourseOfferingId(), offering.getCourseVersionId(), item.getCourseItemId());
      if (!Objects.equals(execution.getAssessmentExecutionId(), result.assessmentExecutionId())
        || !Objects.equals(execution.getSourceType(), result.sourceType())) {
        throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "考核执行与课程报名不匹配");
      }

      CourseAssessmentResult effective = new CourseAssessmentResult();
      effective.setAssessmentExecutionId(result.assessmentExecutionId());
      effective.setExamCandidateId(result.examCandidateId());
      effective.setExamAttemptId(result.examAttemptId());
      effective.setCourseEnrollmentId(result.courseEnrollmentId());
      effective.setCourseOfferingId(result.courseOfferingId());
      effective.setCourseItemId(result.courseItemId());
      effective.setUserId(result.userId());
      effective.setSourceType(result.sourceType());
      effective.setAttemptNo(result.attemptNo());
      effective.setFinalScore(result.finalScore());
      effective.setIsPassed(result.isPassed());
      effective.setCorrectCount(result.correctCount());
      effective.setWrongCount(result.wrongCount());
      effective.setEffectiveTime(result.effectiveTime());
      try {
        assessmentResultMapper.insert(effective);
      } catch (DuplicateKeyException duplicate) {
        return;
      }

      // 迟到的首测回调不能覆盖已生效的补测进度。
      if (isFinalAssessment(item) && assessmentResultMapper.selectCount(new LambdaQueryWrapper<CourseAssessmentResult>()
        .eq(CourseAssessmentResult::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())
        .eq(CourseAssessmentResult::getCourseItemId, item.getCourseItemId())
        .gt(CourseAssessmentResult::getAttemptNo, result.attemptNo()).last("FOR UPDATE")) > 0) return;
      CourseItemProgress progress = itemProgressMapper.selectOne(
        new LambdaQueryWrapper<CourseItemProgress>()
          .eq(CourseItemProgress::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())
          .eq(CourseItemProgress::getCourseItemId, item.getCourseItemId()).last("FOR UPDATE"));
      boolean created = progress == null;
      if (created) {
        progress = new CourseItemProgress();
        progress.setCourseEnrollmentId(enrollment.getCourseEnrollmentId());
        progress.setCourseItemId(item.getCourseItemId());
        progress.setProgressStatus(PROGRESS_NOT_STARTED);
        progress.setStudySeconds(0);
        progress.setAttemptCount(0);
        progress.setFirstStartTime(result.effectiveTime());
      }
      // 交卷阶段已按本次作答记过 1 次，生效回调只保证至少 1 次，避免同一份答卷重复计数。
      int settledAttempts = progress.getAttemptCount() == null ? 0 : progress.getAttemptCount();
      progress.setAttemptCount(Math.max(settledAttempts, isFinalAssessment(item) ? result.attemptNo() : 1));
      if (isFinalAssessment(item) && !Integer.valueOf(1).equals(result.isPassed())) {
        progress.setProgressStatus(PROGRESS_LEARNING);
        progress.setCompletedTime(null);
      } else if (!PROGRESS_COMPLETED.equals(progress.getProgressStatus())) {
        progress.setProgressStatus(PROGRESS_COMPLETED);
        progress.setCompletedTime(result.effectiveTime());
      }
      if (result.finalScore() != null && (progress.getBestScore() == null
        || result.finalScore().compareTo(progress.getBestScore()) > 0)) {
        progress.setBestScore(result.finalScore());
      }
      progress.setLastStudyTime(result.effectiveTime());
      if (created) {
        itemProgressMapper.insert(progress);
      } else {
        itemProgressMapper.updateById(progress);
      }
      recomputeEnrollment(enrollment, offering, snapshot);
    });
  }

  private void upsertItemProgress(CourseEnrollment enrollment,
                                  ItemSnapshot item, StudySessionReportRequest request) {
    LocalDateTime now = LocalDateTime.now();
    CourseItemProgress progress = itemProgressMapper.selectOne(
      new LambdaQueryWrapper<CourseItemProgress>()
        .eq(CourseItemProgress::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())
        .eq(CourseItemProgress::getCourseItemId, request.getCourseItemId()).last("FOR UPDATE"));

    int addedSeconds = request.getValidStudySeconds() != null ? request.getValidStudySeconds() : 0;
    if (progress == null) {
      progress = new CourseItemProgress();
      progress.setCourseEnrollmentId(enrollment.getCourseEnrollmentId());
      progress.setCourseItemId(request.getCourseItemId());
      progress.setProgressStatus(PROGRESS_LEARNING);
      progress.setStudySeconds(addedSeconds);
      progress.setAttemptCount(1);
      progress.setFirstStartTime(request.getStartedTime());
      progress.setLastStudyTime(now);
      applySessionPosition(progress, request);
      maybeCompleteItem(progress, item);
      itemProgressMapper.insert(progress);
      return;
    }

    progress.setStudySeconds(progress.getStudySeconds() + addedSeconds);
    progress.setAttemptCount(progress.getAttemptCount() + 1);
    progress.setLastStudyTime(now);
    if (PROGRESS_NOT_STARTED.equals(progress.getProgressStatus())) {
      progress.setProgressStatus(PROGRESS_LEARNING);
    }
    applySessionPosition(progress, request);
    maybeCompleteItem(progress, item);
    itemProgressMapper.updateById(progress);
  }

  private void applySessionPosition(CourseItemProgress progress, StudySessionReportRequest request) {
    if (request.getEndPositionSeconds() != null) {
      progress.setLastPositionSeconds(request.getEndPositionSeconds());
    }
    if (request.getEndPageNo() != null) {
      progress.setLastPageNo(request.getEndPageNo());
    }
  }

  private void maybeCompleteItem(CourseItemProgress progress, ItemSnapshot item) {
    if (PROGRESS_COMPLETED.equals(progress.getProgressStatus())) {
      return;
    }
    // 考试必须等待成绩发布回调，直播必须等待独立的直播参与事实，不能按普通素材时长自动完成。
    if (!"ASSET".equals(item.getItemType())) {
      return;
    }
    int requiredSeconds = resolveRequiredSeconds(item);
    // 阈值为 0 表示该内容项未配置学习时长要求，不得据此直接判定完成。
    if (requiredSeconds <= 0) {
      return;
    }
    if (progress.getStudySeconds() >= requiredSeconds) {
      progress.setProgressStatus(PROGRESS_COMPLETED);
      progress.setCompletedTime(LocalDateTime.now());
    }
  }

  /**
   * 解析 ASSET 内容项的完成阈值。新快照只读取内容项要求学习时长；
   * 旧快照继续按「自定义规则 → 内容项时长 → 旧默认规则」解析。
   */
  private int resolveRequiredSeconds(ItemSnapshot item) {
    MaterialRuleSnapshot rule = item.getMaterialRule();
    if (rule != null && MATERIAL_RULE_SOURCE_CUSTOM.equals(rule.getSource())
      && isPositiveSeconds(rule.getRequiredSeconds())) {
      return rule.getRequiredSeconds();
    }
    if (isPositiveSeconds(item.getMinimumStudySeconds())) {
      return item.getMinimumStudySeconds();
    }
    if (rule != null && isPositiveSeconds(rule.getRequiredSeconds())) {
      return rule.getRequiredSeconds();
    }
    return 0;
  }

  private static boolean isPositiveSeconds(Integer seconds) {
    return seconds != null && seconds > 0;
  }

  private void recomputeEnrollment(CourseEnrollment enrollment, CourseOffering offering,
                                   CourseContentSnapshot snapshot) {
    List<ItemSnapshot> allItems = flattenItems(snapshot);
    if (allItems.isEmpty()) {
      return;
    }

    Map<Long, CourseItemProgress> progressMap = itemProgressMapper.selectList(
        new LambdaQueryWrapper<CourseItemProgress>()
          .eq(CourseItemProgress::getCourseEnrollmentId, enrollment.getCourseEnrollmentId()).last("FOR UPDATE"))
      .stream()
      .collect(Collectors.toMap(CourseItemProgress::getCourseItemId, Function.identity()));

    int totalStudySeconds = progressMap.values().stream()
      .mapToInt(p -> p.getStudySeconds() != null ? p.getStudySeconds() : 0)
      .sum();

    List<ItemSnapshot> relevantItems = relevantItems(allItems, offering.getCompletionRule());
    long completedCount = relevantItems.stream()
      .filter(item -> {
        CourseItemProgress progress = progressMap.get(item.getCourseItemId());
        return progress != null && PROGRESS_COMPLETED.equals(progress.getProgressStatus());
      })
      .count();

    BigDecimal progressPercent = relevantItems.isEmpty()
      ? BigDecimal.ZERO
      : BigDecimal.valueOf(completedCount * 100.0 / relevantItems.size())
      .setScale(2, RoundingMode.HALF_UP);

    enrollment.setTotalStudySeconds(totalStudySeconds);
    enrollment.setProgressPercent(progressPercent);
    enrollment.setLastStudyTime(LocalDateTime.now());

    boolean finalRule = allItems.stream().anyMatch(this::isFinalAssessment);
    if (finalRule && enrollment.getRecognizedStudySeconds() == null) enrollment.setRecognizedStudySeconds(0);
    String previousStatus = enrollment.getLearningStatus();
    if (!LEARNING_COMPLETED.equals(enrollment.getLearningStatus())) {
      if (LEARNING_NOT_STARTED.equals(enrollment.getLearningStatus())) {
        enrollment.setLearningStatus(LEARNING_LEARNING);
      }
      if (isEnrollmentCompleted(allItems, progressMap, offering.getCompletionRule())) {
        enrollment.setLearningStatus(LEARNING_COMPLETED);
        if (finalRule) enrollment.setRecognizedStudySeconds(totalStudySeconds);
        enrollment.setCompletedTime(LocalDateTime.now());
        enrollment.setProgressPercent(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
      }
    }

    boolean scoreChanged = fillTrainingFinalScore(enrollment, offering, snapshot);
    enrollmentMapper.updateById(enrollment);
    if (!Objects.equals(previousStatus, enrollment.getLearningStatus()) || scoreChanged) {
      refreshTrainingProjection(enrollment, offering);
    }
    issueCertificateForIndependentCourse(enrollment, offering, previousStatus);
  }

  /**
   * 独立课程（不属于任何培训计划）首次达到 COMPLETED 时，以课程主数据标识触发证书规则。
   * 培训计划内的开课由培训结果统一发证，不在此处触发。
   */
  private void issueCertificateForIndependentCourse(CourseEnrollment enrollment, CourseOffering offering,
                                                    String previousStatus) {
    if (offering == null || offering.getPlanContentId() != null) {
      return;
    }
    if (LEARNING_COMPLETED.equals(previousStatus) || !LEARNING_COMPLETED.equals(enrollment.getLearningStatus())) {
      return;
    }
    certificateAutoIssueService.issueForSource("COURSE", offering.getCourseId(), enrollment.getUserId(),
                                               enrollment.getCourseEnrollmentId());
  }

  /**
   * 课程正式成绩只服务于培训结果：独立课程维持原有“章节测验各自出分”的语义。
   */
  private boolean fillTrainingFinalScore(CourseEnrollment enrollment, CourseOffering offering,
                                         CourseContentSnapshot snapshot) {
    if (flattenItems(snapshot).stream().anyMatch(this::isFinalAssessment)
      && !LEARNING_COMPLETED.equals(enrollment.getLearningStatus())) return false;
    if (enrollment.getFinalScore() != null) {
      return false;
    }
    BigDecimal score = TrainingCourseScoreCalculator.calculate(enrollment, offering, snapshot,
      examCandidateMapper.selectList(new LambdaQueryWrapper<ExamCandidate>()
        .eq(ExamCandidate::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())));
    if (score == null) {
      return false;
    }
    enrollment.setFinalScore(score);
    return true;
  }

  private void refreshTrainingProjection(CourseEnrollment enrollment, CourseOffering offering) {
    if (enrollment.getTrainingParticipantId() == null || offering.getPlanContentId() == null) {
      return;
    }
    eventPublisher.publishEvent(new TrainingParticipantContentFactChangedEvent(
      this, enrollment.getTrainingParticipantId(), offering.getPlanContentId()));
  }

  private boolean isEnrollmentCompleted(List<ItemSnapshot> allItems,
                                        Map<Long, CourseItemProgress> progressMap,
                                        String completionRule) {
    List<ItemSnapshot> targets = relevantItems(allItems, completionRule);
    if (targets.isEmpty()) {
      return false;
    }
    return targets.stream().allMatch(item -> {
      CourseItemProgress progress = progressMap.get(item.getCourseItemId());
      return progress != null && PROGRESS_COMPLETED.equals(progress.getProgressStatus());
    });
  }

  /** 旧快照未设置结业标记时，继续使用普通测验完成规则。 */
  private boolean isFinalAssessment(ItemSnapshot item) {
    return "QUIZ".equals(item.getItemType()) && Integer.valueOf(1).equals(item.getIsFinalAssessment())
      && Integer.valueOf(1).equals(item.getStatus());
  }

  @Override
  public void assertFinalAssessmentReady(Long enrollmentId, Long itemId, Long userId) {
    CourseEnrollment enrollment = requireEnrollment(enrollmentId);
    assertEnrollmentOwner(enrollment, StpUtil.getLoginIdAsLong());
    assertEnrollmentOwner(enrollment, userId);
    CourseOffering offering = requireOffering(enrollment.getCourseOfferingId());
    assertOfferingLearnable(offering);
    if (!Set.of("NOT_STARTED", "LEARNING", "COMPLETED").contains(enrollment.getLearningStatus())) {
      throw new BusinessException(HttpStatus.CONFLICT, 5086, "当前报名不可学习");
    }
    CourseContentSnapshot snapshot = loadSnapshot(offering);
    ItemSnapshot target = findItem(snapshot, itemId);
    if (target == null || !isFinalAssessment(target)
      || snapshot.getChapters().stream().noneMatch(ch -> Integer.valueOf(1).equals(ch.getStatus())
        && ch.getItems() != null && ch.getItems().contains(target))) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5084, "结业测评不存在或已停用");
    }
    Map<Long, CourseItemProgress> progress = itemProgressMapper.selectList(new LambdaQueryWrapper<CourseItemProgress>()
      .eq(CourseItemProgress::getCourseEnrollmentId, enrollmentId)).stream()
      .collect(Collectors.toMap(CourseItemProgress::getCourseItemId, Function.identity()));
    if (!finalAssessmentReady(snapshot, offering, progress, itemId)) {
      throw new BusinessException(HttpStatus.CONFLICT, 5087, "请先完成结业测评之外的必需内容");
    }
  }

  /** 沿用开班完成规则，只排除结业测评本身。 */
  private boolean finalAssessmentReady(CourseContentSnapshot snapshot, CourseOffering offering,
                                       Map<Long, CourseItemProgress> progress, Long finalItemId) {
    return relevantItems(flattenItems(snapshot), offering.getCompletionRule()).stream()
      .filter(item -> !java.util.Objects.equals(finalItemId, item.getCourseItemId()))
      .allMatch(item -> progress.containsKey(item.getCourseItemId())
        && PROGRESS_COMPLETED.equals(progress.get(item.getCourseItemId()).getProgressStatus()));
  }

  private List<ItemSnapshot> relevantItems(List<ItemSnapshot> allItems, String completionRule) {
    if (COMPLETION_ALL_ITEMS.equals(completionRule)) {
      return allItems;
    }
    return allItems.stream()
      .filter(item -> isFinalAssessment(item) || Integer.valueOf(1).equals(item.getIsRequired()))
      .toList();
  }

  private List<ItemSnapshot> flattenItems(CourseContentSnapshot snapshot) {
    if (snapshot.getChapters() == null) {
      return List.of();
    }
    List<ItemSnapshot> items = new ArrayList<>();
    for (ChapterSnapshot chapter : snapshot.getChapters()) {
      if (chapter.getItems() != null) {
        items.addAll(chapter.getItems());
      }
    }
    return items;
  }

  private ItemSnapshot findItem(CourseContentSnapshot snapshot, Long courseItemId) {
    return flattenItems(snapshot).stream()
      .filter(item -> courseItemId.equals(item.getCourseItemId()))
      .findFirst()
      .orElse(null);
  }

  private CourseContentSnapshot loadSnapshot(CourseOffering offering) {
    if (offering.getCourseVersionId() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "开课未绑定课程版本快照");
    }
    CourseVersion version = courseVersionMapper.selectById(offering.getCourseVersionId());
    if (version == null || version.getContentSnapshotJson() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }
    CourseContentSnapshot snapshot = snapshotAssembler.parseContentSnapshot(version.getContentSnapshotJson());
    if (snapshot == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }
    return snapshot;
  }

  private CourseEnrollmentDetailVO buildEnrollmentDetail(CourseEnrollment enrollment, boolean includeSessions) {
    CourseOffering offering = requireOffering(enrollment.getCourseOfferingId());
    CourseContentSnapshot snapshot = loadSnapshot(offering);
    if (snapshot.getCourse() == null) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5083, "课程版本快照不存在");
    }
    List<CourseItemProgress> progresses = itemProgressMapper.selectList(
      new LambdaQueryWrapper<CourseItemProgress>()
        .eq(CourseItemProgress::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())
        .orderByAsc(CourseItemProgress::getCourseItemId));

    CourseEnrollmentDetailVO vo = new CourseEnrollmentDetailVO();
    vo.setCourseEnrollmentId(enrollment.getCourseEnrollmentId());
    vo.setCourseOfferingId(enrollment.getCourseOfferingId());
    vo.setUserId(enrollment.getUserId());
    vo.setEnrollmentSource(enrollment.getEnrollmentSource());
    vo.setEnrolledTime(enrollment.getEnrolledTime());
    vo.setLearningStatus(enrollment.getLearningStatus());
    vo.setProgressPercent(enrollment.getProgressPercent());
    vo.setTotalStudySeconds(enrollment.getTotalStudySeconds());
    vo.setRecognizedStudySeconds(enrollment.getRecognizedStudySeconds());
    vo.setCreditAwarded(enrollment.getCreditAwarded());
    vo.setCompletedTime(enrollment.getCompletedTime());
    vo.setLastStudyTime(enrollment.getLastStudyTime());
    Map<Long, CourseAssessmentResult> latestResults = new LinkedHashMap<>();
    for (CourseAssessmentResult result : assessmentResultMapper.selectList(
      new LambdaQueryWrapper<CourseAssessmentResult>().eq(CourseAssessmentResult::getCourseEnrollmentId, enrollment.getCourseEnrollmentId()))) {
      latestResults.merge(result.getCourseItemId(), result, (old, next) ->
        (next.getAttemptNo() == null ? 0 : next.getAttemptNo()) > (old.getAttemptNo() == null ? 0 : old.getAttemptNo()) ? next : old);
    }
    vo.setAssessmentResults(latestResults.values().stream().map(result -> {
      var report = new com.sts.course.vo.CourseAssessmentResultVO();
      org.springframework.beans.BeanUtils.copyProperties(result, report);
      int correct = result.getCorrectCount() == null ? 0 : result.getCorrectCount();
      int wrong = result.getWrongCount() == null ? 0 : result.getWrongCount();
      report.setAccuracyPercent(correct + wrong == 0 ? null : BigDecimal.valueOf(correct * 100L)
        .divide(BigDecimal.valueOf(correct + wrong), 2, RoundingMode.HALF_UP));
      return report;
    }).toList());
    vo.setCourse(resourceAssembler.toCourse(snapshot.getCourse()));
    vo.setDownloadAssets(resourceAssembler.toDownloadAssets(
      enrollment.getCourseEnrollmentId(), snapshot));
    vo.setItemProgresses(progresses.stream().map(this::toProgressVO).toList());

    CourseEvaluation evaluation = findEvaluation(enrollment.getCourseEnrollmentId());
    if (evaluation != null) {
      vo.setEvaluation(toEvaluationVO(evaluation));
    }

    if (includeSessions) {
      List<CourseStudySession> sessions = studySessionMapper.selectList(
        new LambdaQueryWrapper<CourseStudySession>()
          .eq(CourseStudySession::getCourseEnrollmentId, enrollment.getCourseEnrollmentId())
          .orderByDesc(CourseStudySession::getStartedTime)
          .last("LIMIT " + RECENT_SESSION_LIMIT));
      vo.setRecentSessions(sessions.stream().map(this::toSessionSummary).toList());
    }

    return vo;
  }

  private CourseStudySession findSessionByTrace(Long enrollmentId, String clientTraceId) {
    return studySessionMapper.selectOne(
      new LambdaQueryWrapper<CourseStudySession>()
        .eq(CourseStudySession::getCourseEnrollmentId, enrollmentId)
        .eq(CourseStudySession::getClientTraceId, clientTraceId));
  }

  private CourseEvaluation findEvaluation(Long enrollmentId) {
    return evaluationMapper.selectOne(
      new LambdaQueryWrapper<CourseEvaluation>()
        .eq(CourseEvaluation::getCourseEnrollmentId, enrollmentId));
  }

  /**
   * 学习互斥：同一账户同时只允许一个活跃学习上下文（跨终端、跨内容），
   * 同一终端不允许其他账户同时学习。已在幂等路径入库的追踪标识不再重复校验。
   */
  private void assertNoParallelLearning(Long userId, StudySessionReportRequest request) {
    LocalDateTime since = LocalDateTime.now().minusSeconds(ACTIVE_SESSION_WINDOW_SECONDS);
    CourseStudySession otherContext = studySessionMapper.selectOtherActiveContext(
      userId, request.getEnrollmentId(), request.getCourseItemId(), request.getDeviceId(), since);
    if (otherContext != null) {
      throw new BusinessException(HttpStatus.CONFLICT, 5095, "该账户正在其他终端或培训内容中学习，请先结束当前学习");
    }
    CourseStudySession otherUserOnDevice = studySessionMapper.selectOtherUserActiveOnDevice(
      request.getDeviceId(), userId, since);
    if (otherUserOnDevice != null) {
      throw new BusinessException(HttpStatus.CONFLICT, 5096, "当前终端正在学习其他账户的内容，请先结束");
    }
  }

  private CourseStudySession buildSession(Long userId, StudySessionReportRequest request) {
    CourseStudySession session = new CourseStudySession();
    session.setCourseEnrollmentId(request.getEnrollmentId());
    session.setCourseItemId(request.getCourseItemId());
    session.setUserId(userId);
    session.setDeviceType(request.getDeviceType());
    session.setDeviceId(request.getDeviceId());
    session.setStartedTime(request.getStartedTime());
    session.setEndedTime(request.getEndedTime());
    session.setValidStudySeconds(request.getValidStudySeconds());
    session.setStartPositionSeconds(request.getStartPositionSeconds());
    session.setEndPositionSeconds(request.getEndPositionSeconds());
    session.setStartPageNo(request.getStartPageNo());
    session.setEndPageNo(request.getEndPageNo());
    session.setEndReason(request.getEndReason());
    session.setClientTraceId(request.getClientTraceId());
    return session;
  }

  private void assertOfferingLearnable(CourseOffering offering) {
    String status = offering.getPublishStatus();
    if (STATUS_CANCELLED.equals(status)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5086, "开课已取消，不能继续学习");
    }
    if (!STATUS_PUBLISHED.equals(status) && !STATUS_ENDED.equals(status)) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 5081, "当前开课状态不允许学习");
    }
  }

  private void assertEnrollmentOwner(CourseEnrollment enrollment, Long userId) {
    if (!userId.equals(enrollment.getUserId())) {
      throw new BusinessException(HttpStatus.FORBIDDEN, 5081, "无权访问该报名学习数据");
    }
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

  private CourseItemProgressVO toProgressVO(CourseItemProgress progress) {
    CourseItemProgressVO vo = new CourseItemProgressVO();
    vo.setItemProgressId(progress.getItemProgressId());
    vo.setCourseItemId(progress.getCourseItemId());
    vo.setProgressStatus(progress.getProgressStatus());
    vo.setStudySeconds(progress.getStudySeconds());
    vo.setLastPositionSeconds(progress.getLastPositionSeconds());
    vo.setLastPageNo(progress.getLastPageNo());
    vo.setAttemptCount(progress.getAttemptCount());
    vo.setFirstStartTime(progress.getFirstStartTime());
    vo.setCompletedTime(progress.getCompletedTime());
    vo.setLastStudyTime(progress.getLastStudyTime());
    return vo;
  }

  static String courseCoverUrl(Long courseId, Long coverFileId) {
    return courseId == null || coverFileId == null ? null : "/api/course/courses/" + courseId + "/cover";
  }

  private CourseEvaluationVO toEvaluationVO(CourseEvaluation evaluation) {
    CourseEvaluationVO vo = new CourseEvaluationVO();
    vo.setCourseEvaluationId(evaluation.getCourseEvaluationId());
    vo.setRating(evaluation.getRating());
    vo.setContent(evaluation.getContent());
    vo.setIsAnonymous(evaluation.getIsAnonymous());
    vo.setSubmittedTime(evaluation.getSubmittedTime());
    return vo;
  }

  private CourseStudySessionSummaryVO toSessionSummary(CourseStudySession session) {
    CourseStudySessionSummaryVO vo = new CourseStudySessionSummaryVO();
    vo.setStudySessionId(session.getStudySessionId());
    vo.setCourseItemId(session.getCourseItemId());
    vo.setDeviceType(session.getDeviceType());
    vo.setStartedTime(session.getStartedTime());
    vo.setEndedTime(session.getEndedTime());
    vo.setValidStudySeconds(session.getValidStudySeconds());
    vo.setEndPositionSeconds(session.getEndPositionSeconds());
    vo.setEndPageNo(session.getEndPageNo());
    vo.setEndReason(session.getEndReason());
    vo.setClientTraceId(session.getClientTraceId());
    return vo;
  }
}

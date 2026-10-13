package com.sts.exam.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.course.entity.CourseEnrollment;
import com.sts.course.mapper.CourseEnrollmentMapper;
import com.sts.course.service.CourseLearningService;
import com.sts.course.service.CourseAssessmentAttemptService;
import com.sts.course.vo.LearnerCourseTaskItemVO;
import com.sts.exam.dto.ExamLearnerTaskQuery;
import com.sts.exam.entity.*;
import com.sts.exam.mapper.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.*;

/** 聚合个人正式考试和所有有效报名的课程随堂测试，原作答领域保持不变。 */
@Service
@RequiredArgsConstructor
public class LearnerAssessmentCenterService {
  /** 原学员正式考试任务与结果。 */
  private final ExamLearnerService exams;
  /** 原报名课程内容目录。 */
  private final CourseLearningService courses;
  /** 本人课程报名范围。 */
  private final CourseEnrollmentMapper enrollments;
  /** 本人历史考核人员关联。 */
  private final ExamCandidateMapper candidates;
  /** 正式作答历史持久化。 */
  private final ExamAttemptMapper attempts;
  /** 原课程报名/课程项作答授权。 */
  private final CourseAssessmentAttemptService courseAttempts;

  /** 列表筛选只影响读模型，不创造考试人员或课程报名。 */
  @Data public static class Query {
    /** ALL、UNFINISHED、IN_PROGRESS、ENDED。 */
    private String status;
    /** 考试/课程名称。 */
    private String keyword;
  }
  /**
   * 两种来源分别携带自己的业务引用，不伪造examId。
   * <p>
   * {@code examType} 仅正式考试给出（ONLINE/OFFLINE/SPECIAL_PRACTICE/BANK_PRACTICE），课程测验为 null；
   * {@code action} 复用原领域的学员建议动作（START/CONTINUE/WAIT_REVIEW/VIEW_RESULT），
   * 为空表示当前没有任何可进入的入口（例如线下考试尚未录分）。
   */
  public record Task(String key, String kind, String title, String sourceName, String examType, String status,
                     boolean available, String action, Long candidateId, Long examId, Long enrollmentId,
                     Long itemId, Long attemptId) { }

  /** 学员端可进入的动作；WAIT_REVIEW 是否有入口取决于是否已存在作答记录。 */
  private static final Set<String> ENTERABLE_ACTIONS = Set.of("START", "CONTINUE", "VIEW_RESULT");
  /** 已提交历史答卷的受控跳转引用。 */
  public record History(Long attemptId, String status, LocalDateTime submittedTime) { }

  /** 合并原正式考试任务与有效报名中的课程测验。 */
  public PageResult<Task> tasks(PageRequest<Query> request) {
    List<Task> rows = collectTasks(false);
    Query query = request.getQuery();
    rows = rows.stream().filter(t -> query == null || query.getStatus() == null || query.getStatus().isBlank()
      || "ALL".equals(query.getStatus()) || query.getStatus().equals(t.status()))
      .filter(t -> query == null || query.getKeyword() == null || query.getKeyword().isBlank()
        || (t.title() != null && t.title().contains(query.getKeyword())) || (t.sourceName() != null && t.sourceName().contains(query.getKeyword()))).toList();
    int start = (int)Math.min((long)(request.getPageNum() - 1) * request.getPageSize(), rows.size());
    return new PageResult<>(rows.subList(start, Math.min(start + request.getPageSize(), rows.size())), rows.size());
  }

  /** 首页一次读取已分配考核，包含独立练习并沿用原考试动作，避免分页重复聚合。 */
  public List<Task> listHomeTasks() { return collectTasks(true); }

  /** 复用本人领域裁剪；考核中心保持原正式考试范围，首页承接全部已安排考试。 */
  private List<Task> collectTasks(boolean includeAssignedPractices) {
    List<Task> rows = new ArrayList<>();
    int page = 1;
    while (true) {
      var original = exams.pageMyTasks(new PageRequest<>(page++, 200, new ExamLearnerTaskQuery()));
      for (var task : original.list()) {
        if (!includeAssignedPractices && !Set.of("ONLINE", "OFFLINE").contains(task.getExamType())) continue;
        String action = task.getAvailableAction();
        rows.add(new Task("exam-" + task.getExamCandidateId(), "FORMAL_EXAM", task.getExamName(), "管理员安排的正式考试",
          task.getExamType(), status(task.getTaskStatus()), enterable(action, task.getCurrentAttemptId()), action,
          task.getExamCandidateId(), task.getExamId(), null, null, task.getCurrentAttemptId()));
      }
      if ((long)(page - 1) * 200 >= original.total()) break;
    }
    // ponytail: 复用每份报名的目录裁剪；报名量很大时再改为批量目录查询。
    for (CourseEnrollment enrollment : enrollments.selectList(new LambdaQueryWrapper<CourseEnrollment>()
      .eq(CourseEnrollment::getUserId, StpUtil.getLoginIdAsLong())
      .in(CourseEnrollment::getLearningStatus, List.of("NOT_STARTED", "LEARNING", "COMPLETED"))
      .orderByDesc(CourseEnrollment::getCourseEnrollmentId))) {
      var tasks = courses.getMyEnrollmentTasks(enrollment.getCourseEnrollmentId());
      for (var chapter : tasks.getChapters()) for (var item : chapter.getItems()) {
        if (!"QUIZ".equals(item.getItemType())) continue;
        rows.add(new Task("course-" + enrollment.getCourseEnrollmentId() + "-" + item.getCourseItemId(), "COURSE_QUIZ",
          item.getItemTitle(), tasks.getCourseName(), null, status(item.getExamTaskStatus()),
          Boolean.TRUE.equals(item.getLearnable()) || item.getPublishedExamAttemptId() != null,
          courseAction(item), null, null, enrollment.getCourseEnrollmentId(), item.getCourseItemId(),
          item.getPublishedExamAttemptId() == null ? item.getActiveExamAttemptId() : item.getPublishedExamAttemptId()));
      }
    }
    return rows;
  }

  /** 历史答卷入口校验本人，并继续使用原领域的详情可见策略。 */
  public List<History> history(Long candidateId, Long enrollmentId, Long itemId) {
    if ((candidateId == null) == (enrollmentId == null) || enrollmentId != null && itemId == null) throw denied();
    Long userId = StpUtil.getLoginIdAsLong();
    LambdaQueryWrapper<ExamCandidate> query = new LambdaQueryWrapper<ExamCandidate>().eq(ExamCandidate::getUserId, userId);
    if (candidateId != null) query.eq(ExamCandidate::getExamCandidateId, candidateId);
    else {
      CourseEnrollment enrollment = enrollments.selectById(enrollmentId);
      if (enrollment == null || !Objects.equals(enrollment.getUserId(), userId)) throw denied();
      query.eq(ExamCandidate::getCourseEnrollmentId, enrollmentId).eq(ExamCandidate::getCourseItemId, itemId);
    }
    var candidate = candidates.selectOne(query);
    if (candidate == null) return List.of();
    List<History> result = new ArrayList<>();
    for (ExamAttempt attempt : attempts.selectList(new LambdaQueryWrapper<ExamAttempt>()
      .eq(ExamAttempt::getUserId, userId).eq(ExamAttempt::getExamCandidateId, candidate.getExamCandidateId())
      .isNotNull(ExamAttempt::getSubmittedTime).ne(ExamAttempt::getAttemptStatus, "VOID")
      .orderByDesc(ExamAttempt::getExamAttemptId))) {
      var view = enrollmentId == null ? exams.getMyAttempt(attempt.getExamAttemptId())
        : courseAttempts.get(enrollmentId, itemId, attempt.getExamAttemptId());
      // 只有已允许查看答案详情的尝试才生成历史考卷入口。
      if (view.getQuestions().stream().anyMatch(q -> q.getCorrectAnswerSnapshotJson() != null))
        result.add(new History(attempt.getExamAttemptId(), attempt.getAttemptStatus(), attempt.getSubmittedTime()));
    }
    return result;
  }
  /** 将原考核状态映射到聚合筛选分类。 */
  private String status(String value) {
    if ("IN_PROGRESS".equals(value)) return "IN_PROGRESS";
    return Set.of("WAITING_RESULT", "RESULT_PUBLISHED", "RESULT_EFFECTIVE").contains(value == null ? "" : value) ? "ENDED" : "UNFINISHED";
  }
  /**
   * 是否存在学员可进入的入口。
   * <p>
   * 历史缺陷：原先用 {@code !"NONE".equals(action)} 判断，而原领域从不产生 "NONE"，
   * 导致线下考试未录分（action 为空）也被标为可进入，前端会给出一个必然失败的入口。
   */
  private boolean enterable(String action, Long attemptId) {
    if (action == null) return false;
    if (ENTERABLE_ACTIONS.contains(action)) return true;
    // WAIT_REVIEW 表示已提交待批阅：只有已存在作答记录时才允许回看答题情况。
    return "WAIT_REVIEW".equals(action) && attemptId != null;
  }
  /** 课程测验没有独立 examType，动作按课程项自身的考核状态推导，保持与原入口一致。 */
  private String courseAction(LearnerCourseTaskItemVO item) {
    String value = item.getExamTaskStatus() == null ? "" : item.getExamTaskStatus();
    if ("RESULT_PUBLISHED".equals(value) || "RESULT_EFFECTIVE".equals(value)) return "VIEW_RESULT";
    if ("IN_PROGRESS".equals(value)) return "CONTINUE";
    if ("WAITING_RESULT".equals(value)) return "WAIT_REVIEW";
    return Boolean.TRUE.equals(item.getLearnable()) || item.getPublishedExamAttemptId() != null ? "START" : null;
  }
  /** 返回本人考核记录访问拒绝。 */
  private BusinessException denied() { return new BusinessException(HttpStatus.FORBIDDEN, 7092, "无权访问该考核记录"); }
}

package com.sts.learner.service;

import cn.dev33.satoken.stp.StpUtil;
import com.sts.common.api.*;
import com.sts.common.exception.BusinessException;
import com.sts.course.service.CourseLearningService;
import com.sts.exam.service.LearnerAssessmentCenterService;
import com.sts.training.service.TrainingWorkflowService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** 本人首页只聚合已存在的领域任务，不创建报名或改变结果。 */
@Service @RequiredArgsConstructor
public class LearnerHomeService {
  /** 原课程报名读取。 */
  private final CourseLearningService courses;
  /** 正式考试及随堂测试受控读取。 */
  private final LearnerAssessmentCenterService assessments;
  /** 原培训任务读取。 */
  private final TrainingWorkflowService training;

  /** 首页分类和名称筛选。 */
  @Data public static class Query {
    /** ALL/COURSE/FORMAL_EXAM/COURSE_QUIZ/TRAINING。 */
    private String kind;
    /** 任务名称。 */
    private String keyword;
  }
  /** 业务引用由两端映射已有入口，不输出任意URL或隐藏答案。 */
  public record Task(String key, String kind, String title, String status, String action,
                     Long resourceId, Long parentId, Long itemId, Long attemptId, LocalDateTime endTime) { }

  /** 分页合并本人课程、考核和培训；不同来源的ID不能混用。 */
  public PageResult<Task> tasks(PageRequest<Query> request) {
    Query query = request.getQuery();
    if (query != null && query.getKind() != null
      && !Set.of("ALL", "COURSE", "FORMAL_EXAM", "COURSE_QUIZ", "TRAINING").contains(query.getKind())) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, 1001, "任务分类不合法");
    }
    String kind = query == null ? null : query.getKind();
    List<Task> rows = new ArrayList<>();
    if (kind == null || "ALL".equals(kind) || "COURSE".equals(kind)) for (int page = 1; ; page++) {
      var result = courses.pageMyEnrollments(new PageRequest<>(page, 200, null));
      for (var c : result.list()) rows.add(new Task("course-" + c.getEnrollmentId(), "COURSE", c.getCourseName(),
        c.getLearningStatus(), "VIEW_COURSE", c.getEnrollmentId(), null, null, null, c.getEndTime()));
      if ((long) page * 200 >= result.total()) break;
    }
    if (kind == null || Set.of("ALL", "FORMAL_EXAM", "COURSE_QUIZ").contains(kind)) {
      for (var a : assessments.listHomeTasks()) rows.add(new Task(a.key(), a.kind(), a.title(), a.status(),
        a.available() ? a.action() : null,
        "FORMAL_EXAM".equals(a.kind()) ? a.candidateId() : a.enrollmentId(), a.examId(), a.itemId(), a.attemptId(), null));
    }
    if (kind == null || "ALL".equals(kind) || "TRAINING".equals(kind)) for (var t : training.listLearnerTasks(StpUtil.getLoginIdAsLong())) rows.add(new Task(
      "training-" + t.getTrainingParticipantId(), "TRAINING", t.getPlanName(), t.getPublishStatus(),
      "VIEW_TRAINING", t.getTrainingParticipantId(), t.getTrainingPlanId(), null, null, t.getEndTime()));
    String keyword = query == null ? null : query.getKeyword();
    List<Task> filtered = rows.stream().filter(t -> kind == null || "ALL".equals(kind) || kind.equals(t.kind()))
      .filter(t -> keyword == null || keyword.isBlank() || t.title() != null && t.title().contains(keyword.trim()))
      .sorted(Comparator.comparing(Task::endTime, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Task::key))
      .toList();
    int from = (int) Math.min((long) (request.getPageNum() - 1) * request.getPageSize(), filtered.size());
    return new PageResult<>(filtered.subList(from, Math.min(from + request.getPageSize(), filtered.size())), filtered.size());
  }
}

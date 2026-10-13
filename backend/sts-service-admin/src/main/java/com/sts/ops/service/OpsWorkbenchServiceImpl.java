package com.sts.ops.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sts.course.entity.Course;
import com.sts.course.mapper.CourseMapper;
import com.sts.exam.entity.Exam;
import com.sts.exam.mapper.ExamMapper;
import com.sts.ops.vo.OpsTodoCategoryVO;
import com.sts.ops.vo.OpsWorkbenchShortcutVO;
import com.sts.ops.vo.OpsWorkbenchSummaryVO;
import com.sts.training.entity.TrainingParticipant;
import com.sts.training.entity.TrainingPlan;
import com.sts.training.mapper.TrainingParticipantMapper;
import com.sts.training.mapper.TrainingPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpsWorkbenchServiceImpl implements OpsWorkbenchService {

  private final CourseMapper courseMapper;
  private final ExamMapper examMapper;
  private final TrainingPlanMapper trainingPlanMapper;
  private final TrainingParticipantMapper trainingParticipantMapper;

  @Override
  public OpsWorkbenchSummaryVO enterpriseSummary() {
    OpsWorkbenchSummaryVO vo = new OpsWorkbenchSummaryVO();
    vo.setShortcuts(grantedShortcuts());
    vo.setPublishedCourseCount(courseMapper.selectCount(courseWrapper()
      .eq(Course::getPublishStatus, "PUBLISHED")));
    vo.setRunningExamCount(examMapper.selectCount(examWrapper()
      .eq(Exam::getPublishStatus, "PUBLISHED")));
    vo.setRunningPlanCount(trainingPlanMapper.selectCount(planWrapper()
      .eq(TrainingPlan::getPublishStatus, "RUNNING")));
    vo.setCurrentParticipantCount(trainingParticipantMapper.selectCount(
      new LambdaQueryWrapper<TrainingParticipant>()
        .ne(TrainingParticipant::getParticipantStatus, "COMPLETED")));
    vo.setEndedPassedCount(countEndedResult("PASSED"));
    vo.setEndedFailedCount(countEndedResult("FAILED"));
    List<OpsTodoCategoryVO> todos = new ArrayList<>();
    addTodo(todos,
            "DRAFT_COURSE",
            "待发布课程",
            courseMapper.selectCount(courseWrapper()
                                       .eq(Course::getPublishStatus, "DRAFT")),
            "course:course:list");
    addTodo(todos,
            "DRAFT_PLAN",
            "待发布培训计划",
            trainingPlanMapper.selectCount(planWrapper()
                                             .eq(TrainingPlan::getPublishStatus,
                                                 "DRAFT")),
            "training:plan:list");
    addTodo(todos, "PENDING_PARTICIPANT", "待确认参训人", trainingParticipantMapper.selectCount(
      new LambdaQueryWrapper<TrainingParticipant>()
        .eq(TrainingParticipant::getParticipantStatus, "PENDING")), "training:plan:list");
    vo.setTodos(todos);
    vo.setOpenTodoCount(todos.stream().mapToLong(OpsTodoCategoryVO::getCount).sum());
    return vo;
  }

  private long countEndedResult(String resultStatus) {
    List<Long> endedPlanIds = trainingPlanMapper.selectList(planWrapper()
                                                              .eq(TrainingPlan::getPublishStatus, "ENDED")
                                                              .select(TrainingPlan::getTrainingPlanId))
      .stream()
      .map(TrainingPlan::getTrainingPlanId)
      .toList();
    if (endedPlanIds.isEmpty()) {
      return 0;
    }
    Long count = trainingParticipantMapper.selectCount(
      new LambdaQueryWrapper<TrainingParticipant>()
        .in(TrainingParticipant::getTrainingPlanId, endedPlanIds)
        .eq(TrainingParticipant::getResultStatus, resultStatus));
    return count == null ? 0 : count;
  }

  private List<OpsWorkbenchShortcutVO> grantedShortcuts() {
    List<OpsWorkbenchShortcutVO> all = List.of(
      new OpsWorkbenchShortcutVO("COURSE", "课程管理", "/admin/business-center/course/center", "course:course:list"),
      new OpsWorkbenchShortcutVO("EXAM", "理论考试", "/admin/business-center/exams/exam", "exam:exam:list"),
      new OpsWorkbenchShortcutVO("TRAINING", "培训管理", "/admin/business-center/training/plan", "training:plan:list"),
      new OpsWorkbenchShortcutVO("CERTIFICATE", "证书管理", "/admin/result/certificate/certificates", "certificate:cert:list"),
      new OpsWorkbenchShortcutVO("ORG_USER", "组织与用户管理", "/admin/system/org/user", "sys:user:list")
    );
    return all.stream().filter(item -> StpUtil.hasPermission(item.getPermission())).toList();
  }

  private void addTodo(List<OpsTodoCategoryVO> todos, String code, String title, Long count, String permission) {
    if (!StpUtil.hasPermission(permission)) {
      return;
    }
    todos.add(new OpsTodoCategoryVO(code, title, count == null ? 0 : count));
  }

  private LambdaQueryWrapper<Course> courseWrapper() {
    return new LambdaQueryWrapper<Course>().eq(Course::getStatus, 1);
  }

  private LambdaQueryWrapper<Exam> examWrapper() {
    return new LambdaQueryWrapper<Exam>().eq(Exam::getStatus, 1);
  }

  private LambdaQueryWrapper<TrainingPlan> planWrapper() {
    return new LambdaQueryWrapper<TrainingPlan>().eq(TrainingPlan::getStatus, 1);
  }
}

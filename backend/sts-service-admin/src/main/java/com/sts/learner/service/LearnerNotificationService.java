package com.sts.learner.service;

import com.sts.admin.tenant.TenantContext;
import com.sts.learner.mapper.LearnerNotificationSourceMapper;
import com.sts.learner.mapper.LearnerNotificationSourceMapper.Target;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/** 业务事件与定时提醒共用同一事件键，消息投递不改变原业务事实。 */
@Service @RequiredArgsConstructor
public class LearnerNotificationService {
  /** 真实分配关系读取。 */
  private final LearnerNotificationSourceMapper sources;
  /** 消息正文与接收人事务持久化。 */
  private final LearnerMessageService messages;

  /** 已成功执行安排或状态变化后，在原业务事务内调用。 */
  public void businessChanged(String family, String action, Long id, String previousState) {
    Long org = TenantContext.requireTenantId();
    if (family.equals("catalog")) {
      String state = sources.courseState(org, id);
      if (state == null || state.equals(previousState)) return;
      String text = action.equals("offline") ? "课程已下架" : "课程已上架或更新";
      messages.send("catalog:" + id + ":" + state + ":" + previousState, "COURSE_CATALOG", text,
        text + "，请查看当前可访问内容。", id, null, null, sources.courseAudience(org, id));
      return;
    }
    List<Target> targets = switch (family) {
      case "course" -> sources.courses(org, id);
      case "exam" -> sources.exams(org, id);
      case "training" -> sources.trainings(org, id);
      default -> List.of();
    };
    for (Target t : targets) {
      if (!action.equals("cancel") && !action.equals("start") && !messages.isPending(t.status())) continue;
      notify(t, "cancel".equals(action) ? "cancelled" : "start".equals(action) ? "started" : "assigned",
        "cancel".equals(action) ? "培训已取消" : "start".equals(action) ? "培训已启动" : "已为你安排学习任务");
    }
    if (family.equals("course")) for (Target t : sources.quizzes(org, id)) if (messages.isPending(t.status())) notify(t, "assigned", "已为你安排随堂测试");
  }

  /** 为现存有效任务补分配通知，并按服务端时间生成开考和截止提醒。 */
  @Transactional
  public void scan(LocalDateTime now) {
    Long org = TenantContext.requireTenantId();
    List<Target> tasks = new ArrayList<>(sources.courses(org, null));
    tasks.addAll(sources.exams(org, null)); tasks.addAll(sources.trainings(org, null));
    tasks.addAll(sources.quizzes(org, null));
    for (Target t : tasks) {
      if (!messages.isPending(t.status())) continue;
      if (t.endTime() != null && !t.endTime().isAfter(now)) continue;
      notify(t, "assigned", "已为你安排学习任务");
      if (t.kind().equals("FORMAL_EXAM") && t.startTime() != null && !t.startTime().isAfter(now))
        notify(t, "opened:" + t.startTime(), "考试已开放，请按时参加");
      if (!t.kind().equals("COURSE_QUIZ") && messages.shouldRemind(now, t.endTime(), t.status()))
        notify(t, "deadline:" + t.endTime(), "任务将在24小时内截止，请及时完成");
    }
  }

  /** 接收人和资源引用纳入唯一键，重复扫描不会反复打扰。 */
  private void notify(Target t, String event, String text) {
    messages.send(t.kind() + ":" + t.resourceId() + ":" + t.itemId() + ":" + event,
      t.kind(), t.title(), text, t.resourceId(), t.parentId(), t.itemId(), List.of(t.userId()));
  }
}
